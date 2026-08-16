package com.mychat.service;

import com.mychat.common.ChatStreamEvent;
import com.mychat.common.RoutingWorkflow;
import com.mychat.config.WorkspaceContext;
import com.mychat.entity.dto.RouteRequest;
import com.mychat.utils.ChatStreamEventWriter;
import com.mychat.utils.NdjsonStreamSupport;
import com.mychat.utils.ObservabilityStreamAdvisor;
import com.mychat.utils.ReasoningContentExtractor;
import com.mychat.utils.SearchSystemPrompts;
import com.mychat.utils.WorkspacePromptBuilder;
import com.mychat.utils.WorkspaceUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Demo 旁路：单次 Routing 的 NDJSON 流式管道。
 * <p>
 * NDJSON sink 辅助与主聊天共用 {@link NdjsonStreamSupport}；
 * 默认不落 {@code chat_assistant_turns}（调试旁路）。
 * <p>
 * {@code qualityLoop=true} 时仅打日志跳过（Demo 轻量，质量环仍走主聊天或
 * {@code /ai/agent/evaluate-optimize}）。
 */
@Slf4j
@Service
public class AgentRouteDemoStreamService {

    private static final String GENERAL_SYSTEM_PROMPT =
            "你是友好的助手，用简洁中文回答用户的一般问题。不要尝试调用外部工具。";

    private static final String SEARCH_SYSTEM_PROMPT = SearchSystemPrompts.SEARCH;

    private final ChatClient toolChatClient;
    private final ChatClient ragChatClient;
    private final AgentRoutingService agentRoutingService;
    private final WorkspaceUtil workspaceUtil;
    private final WorkspacePromptBuilder workspacePromptBuilder;
    private final ChatStreamEventWriter eventWriter;

    public AgentRouteDemoStreamService(
            @Qualifier("toolChatClient") ChatClient toolChatClient,
            @Qualifier("ragChatClient") ChatClient ragChatClient,
            AgentRoutingService agentRoutingService,
            WorkspaceUtil workspaceUtil,
            WorkspacePromptBuilder workspacePromptBuilder,
            ChatStreamEventWriter eventWriter) {
        this.toolChatClient = toolChatClient;
        this.ragChatClient = ragChatClient;
        this.agentRoutingService = agentRoutingService;
        this.workspaceUtil = workspaceUtil;
        this.workspacePromptBuilder = workspacePromptBuilder;
        this.eventWriter = eventWriter;
    }

    /**
     * @throws IllegalArgumentException input 为空
     */
    public Flux<String> stream(RouteRequest request) {
        if (request == null || !StringUtils.hasText(request.getInput())) {
            throw new IllegalArgumentException("input 不能为空");
        }
        String input = request.getInput().trim();
        String kbId = StringUtils.hasText(request.getKbId()) ? request.getKbId().trim() : null;
        String workDir = StringUtils.hasText(request.getWorkDir())
                ? request.getWorkDir().trim()
                : workspaceUtil.getWorkspaceRoot().toString();
        String chatId = StringUtils.hasText(request.getChatId())
                ? request.getChatId().trim()
                : "agent-route-" + UUID.randomUUID();
        boolean qualityLoop = Boolean.TRUE.equals(request.getQualityLoop());

        WorkspaceContext.set(workDir);
        String turnId = chatId + "-" + UUID.randomUUID();
        AtomicInteger seq = new AtomicInteger(0);
        Sinks.Many<ChatStreamEvent> sink = Sinks.many().replay().limit(1024);
        List<ChatStreamEvent> accumulated = Collections.synchronizedList(new ArrayList<>());
        ObservabilityStreamAdvisor obs =
                new ObservabilityStreamAdvisor(turnId, seq, sink, eventWriter.getObjectMapper(), accumulated);

        RoutingWorkflow.RoutingResponse classified =
                agentRoutingService.classify(input, kbId, workDir);
        log.info("Demo ndjson Routing: route={}, reasoning={}, chatId={}",
                classified.selection(), classified.reasoning(), chatId);
        NdjsonStreamSupport.emitTracked(sink, accumulated, ChatStreamEvent.route(
                turnId, seq, classified.selection(), classified.reasoning()));

        AtomicReference<String> lastReasoning = new AtomicReference<>();
        Mono<Void> drive = streamByRoute(
                classified.selection(), input, chatId, kbId, obs, sink, turnId, seq, accumulated, lastReasoning)
                .doOnSuccess(v -> {
                    if (qualityLoop) {
                        log.info("Demo /route qualityLoop=true 已忽略（请用主聊天或 /ai/agent/evaluate-optimize）turnId={}",
                                turnId);
                    }
                })
                .doOnError(e -> NdjsonStreamSupport.emitError(sink, turnId, seq, e, accumulated))
                .doFinally(signal -> {
                    // 旁路：不落 chat_assistant_turns；仅发 done / 结束 sink
                    if (signal == SignalType.ON_COMPLETE) {
                        NdjsonStreamSupport.emitTracked(sink, accumulated, ChatStreamEvent.done(turnId, seq));
                    }
                    sink.tryEmitComplete();
                })
                .then();

        // WorkspaceContext 在整段 NDJSON 消费结束后再清（与主聊天 doFinally 时机一致）
        return NdjsonStreamSupport.mergeNdjson(sink, drive, eventWriter)
                .doFinally(s -> WorkspaceContext.clear());
    }

    private Mono<Void> streamByRoute(
            String route,
            String userText,
            String chatId,
            String kbId,
            ObservabilityStreamAdvisor obs,
            Sinks.Many<ChatStreamEvent> sink,
            String turnId,
            AtomicInteger seq,
            List<ChatStreamEvent> accumulated,
            AtomicReference<String> lastReasoning) {

        Flux<ChatResponse> responses = switch (route) {
            case "kb" -> {
                if (!StringUtils.hasText(kbId)) {
                    yield ragChatClient.prompt()
                            .system(GENERAL_SYSTEM_PROMPT)
                            .user(userText)
                            .advisors(obs)
                            .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, chatId))
                            .stream()
                            .chatResponse();
                }
                yield ragChatClient.prompt()
                        .advisors(agentRoutingService.buildKbAdvisor(kbId))
                        .advisors(obs)
                        .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, chatId))
                        .user(userText)
                        .stream()
                        .chatResponse();
            }
            case "search" -> toolChatClient.prompt()
                    .system(SEARCH_SYSTEM_PROMPT)
                    .user(userText)
                    .advisors(obs)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, chatId))
                    .stream()
                    .chatResponse();
            case "file" -> toolChatClient.prompt()
                    .system(workspacePromptBuilder.build())
                    .user(userText)
                    .advisors(obs)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, chatId))
                    .stream()
                    .chatResponse();
            default -> ragChatClient.prompt()
                    .system(GENERAL_SYSTEM_PROMPT)
                    .user(userText)
                    .advisors(obs)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, chatId))
                    .stream()
                    .chatResponse();
        };

        return responses
                .doOnNext(cr -> emitTextEvents(sink, turnId, seq, cr, accumulated, lastReasoning))
                .then();
    }

    /**
     * 与主聊天 route 目标语义对齐：工具帧也可抽 thinking，不发 text_delta。
     */
    private void emitTextEvents(Sinks.Many<ChatStreamEvent> sink,
                                String turnId,
                                AtomicInteger seq,
                                ChatResponse response,
                                List<ChatStreamEvent> accumulated,
                                AtomicReference<String> lastReasoning) {
        if (response == null || response.getResult() == null) {
            return;
        }
        String thinkingDelta = ReasoningContentExtractor.nextDelta(
                lastReasoning, ReasoningContentExtractor.extract(response));
        if (thinkingDelta != null) {
            NdjsonStreamSupport.emitTracked(sink, accumulated,
                    ChatStreamEvent.thinkingDelta(turnId, seq, thinkingDelta));
        }
        if (response.hasToolCalls()) {
            return;
        }
        String content = response.getResult().getOutput().getText();
        if (content != null && !content.isEmpty()) {
            NdjsonStreamSupport.emitTracked(sink, accumulated,
                    ChatStreamEvent.textDelta(turnId, seq, content));
        }
    }
}
