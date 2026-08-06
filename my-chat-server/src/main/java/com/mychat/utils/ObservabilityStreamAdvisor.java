package com.mychat.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mychat.common.ChatStreamEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.core.Ordered;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 工具循环内侧观测 Advisor（Spring AI 2.0 Recursive Advisors 模式）。
 * <p>
 * <b>代码路径</b>：本类只把 {@code tool_call} / {@code tool_result} 旁路写入请求级 {@link Sinks}，
 * 由 {@code ChatController} merge 成 NDJSON；<b>不</b>改写 {@link ChatClientResponse}，避免污染 Memory。
 * <p>
 * <b>工具循环</b>：仍由自动注册的 {@code ToolCallingAdvisor}（order ≈ HIGHEST_PRECEDENCE+300）负责；
 * 本 Advisor order = +400，落在循环内侧，因而每一轮迭代都能看到工具帧。
 * <p>
 * 官方参考：Recursive Advisors — Observing the Tool-Calling Loop。
 * 注意：2.0 GA 已删除 {@code streamToolCallResponses}，必须用本旁路模式。
 */
@Slf4j
public class ObservabilityStreamAdvisor implements CallAdvisor, StreamAdvisor {

    private final String turnId;
    private final AtomicInteger seq;
    private final Sinks.Many<ChatStreamEvent> sink;
    private final ObjectMapper objectMapper;
    /** 与 Sink 并行累积，供第 3 周落库（避免仅靠 Flux doOnNext 丢事件） */
    private final List<ChatStreamEvent> accumulated;

    /** 已发出 tool_call 的 id，防止流式分片重复 */
    private final Set<String> emittedToolCallIds = new LinkedHashSet<>();
    /** 已发出 tool_result 的 id */
    private final Set<String> emittedToolResultIds = new LinkedHashSet<>();

    public ObservabilityStreamAdvisor(String turnId,
                                      AtomicInteger seq,
                                      Sinks.Many<ChatStreamEvent> sink,
                                      ObjectMapper objectMapper,
                                      List<ChatStreamEvent> accumulated) {
        this.turnId = turnId;
        this.seq = seq;
        this.sink = sink;
        this.objectMapper = objectMapper;
        this.accumulated = accumulated;
    }

    @Override
    public String getName() {
        return "ObservabilityStreamAdvisor";
    }

    @Override
    public int getOrder() {
        // ToolCallingAdvisor.DEFAULT_ORDER ≈ HIGHEST_PRECEDENCE + 300；+400 进入循环内侧
        return Ordered.HIGHEST_PRECEDENCE + 400;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        emitToolResultsFromRequest(request);
        ChatClientResponse response = chain.nextCall(request);
        emitToolCallsFromResponse(response);
        return response;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        emitToolResultsFromRequest(request);
        return chain.nextStream(request).doOnNext(this::emitToolCallsFromResponse);
    }

    /**
     * 后续迭代的 request 会带上上一轮的 {@link ToolResponseMessage} → 发 tool_result。
     */
    private void emitToolResultsFromRequest(ChatClientRequest request) {
        if (request == null || request.prompt() == null) {
            return;
        }
        for (Message message : request.prompt().getInstructions()) {
            if (!(message instanceof ToolResponseMessage)) {
                continue;
            }
            ToolResponseMessage toolResponseMessage = (ToolResponseMessage) message;
            for (ToolResponseMessage.ToolResponse response : toolResponseMessage.getResponses()) {
                String id = response.id();
                if (id == null || !emittedToolResultIds.add(id)) {
                    continue;
                }
                String[] truncated = ChatStreamEvent.truncatePreview(response.responseData());
                boolean truncatedFlag = "true".equals(truncated[1]);
                emit(ChatStreamEvent.toolResult(
                        turnId, seq, id, response.name(), true, truncated[0], truncatedFlag));
            }
        }
    }

    /**
     * 模型返回的工具调用请求（含流式分片）→ 每个 id 只发一次 tool_call。
     */
    private void emitToolCallsFromResponse(ChatClientResponse clientResponse) {
        if (clientResponse == null || clientResponse.chatResponse() == null) {
            return;
        }
        ChatResponse chatResponse = clientResponse.chatResponse();
        if (!chatResponse.hasToolCalls() || chatResponse.getResult() == null) {
            return;
        }
        AssistantMessage assistantMessage = chatResponse.getResult().getOutput();
        if (assistantMessage == null || !assistantMessage.hasToolCalls()) {
            return;
        }
        for (AssistantMessage.ToolCall toolCall : assistantMessage.getToolCalls()) {
            String id = toolCall.id();
            if (id == null || id.isBlank()) {
                continue;
            }
            // 流式过程中 args 可能从空逐渐补全：仅在首次见到时发出（时间线优先保证「已发起调用」可见）
            if (!emittedToolCallIds.add(id)) {
                continue;
            }
            String name = toolCall.name() != null ? toolCall.name() : "";
            Object args = parseArgs(toolCall.arguments());
            emit(ChatStreamEvent.toolCall(turnId, seq, id, name, args));
        }
    }

    private Object parseArgs(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(arguments, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            return ChatStreamEvent.rawArgs(arguments);
        }
    }

    private void emit(ChatStreamEvent event) {
        if (accumulated != null) {
            accumulated.add(event);
        }
        Sinks.EmitResult result = sink.tryEmitNext(event);
        if (result.isFailure()) {
            log.debug("Observability 事件发射失败: {} eventType={}", result, event.type());
        }
    }
}
