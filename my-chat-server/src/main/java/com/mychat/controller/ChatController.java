package com.mychat.controller;

import com.mychat.service.AgentEvaluatorOptimizerService;
import com.mychat.service.AgentOrchestratorService;
import com.mychat.service.AgentRoutingService;
import com.mychat.common.RoutingWorkflow;
import com.mychat.entity.dto.EvaluateOptimizeRequest;
import com.mychat.entity.dto.OrchestrateRequest;
import com.mychat.utils.ObservabilityStreamAdvisor;
import com.mychat.common.ChatStreamEvent;
import com.mychat.utils.ChatStreamEventWriter;
import com.mychat.config.WorkspaceContext;
import com.mychat.service.ChatAssistantTurnService;
import com.mychat.service.ChatSessionsService;
import com.mychat.utils.SearchSystemPrompts;
import com.mychat.utils.WorkspacePromptBuilder;
import com.mychat.utils.WorkspaceUtil;
import com.mychat.utils.WritePathExtractor;
import com.mychat.vo.EvaluateOptimizeResultVO;
import com.mychat.vo.EvaluateOptimizeRoundVO;
import com.mychat.vo.OrchestrateStepVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 主聊天流式端点（默认 Orchestrator + 写盘质量环）。
 * <p>
 * {@code format} 缺省 / {@code plain}：保持历史 {@code text/html} 行为。<br>
 * {@code format=ndjson}：结构化事件流（{@code route} / {@code step} + 工具观测）。
 * <p>
 * 主路默认 {@code agentMode=orchestrate} + {@code qualityLoop=true}。<br>
 * 调试可显式传 {@code agentMode=route} 或 {@code qualityLoop=false} 回退。<br>
 * 可选 {@code kbId}：请求参数优先，否则读会话绑定。<br>
 * 附件仅支持 txt/md/pdf（抽文本后并入 Agent）；图片暂不支持。
 */
@Slf4j
@RestController
@RequestMapping("/ai/normalChat")
public class ChatController {

    private static final String AGENT_MODE_ROUTE = "route";
    private static final String AGENT_MODE_ORCHESTRATE = "orchestrate";
    private static final String DEFAULT_QUALITY_CRITERIA =
            "文件存在、非空，且内容符合用户目标。";

    private final ChatClient toolChatClient;
    private final ChatClient ragChatClient;
    private final AgentRoutingService agentRoutingService;
    private final AgentOrchestratorService agentOrchestratorService;
    private final AgentEvaluatorOptimizerService agentEvaluatorOptimizerService;
    private final ChatSessionsService chatSessionsService;
    private final ChatAssistantTurnService chatAssistantTurnService;
    private final ChatMemory chatMemory;
    private final WorkspaceUtil workspaceUtil;
    private final WorkspacePromptBuilder workspacePromptBuilder;
    private final ChatStreamEventWriter eventWriter;

    public ChatController(
            @Qualifier("toolChatClient") ChatClient toolChatClient,
            @Qualifier("ragChatClient") ChatClient ragChatClient,
            AgentRoutingService agentRoutingService,
            AgentOrchestratorService agentOrchestratorService,
            AgentEvaluatorOptimizerService agentEvaluatorOptimizerService,
            ChatSessionsService chatSessionsService,
            ChatAssistantTurnService chatAssistantTurnService,
            ChatMemory chatMemory,
            WorkspaceUtil workspaceUtil,
            WorkspacePromptBuilder workspacePromptBuilder,
            ChatStreamEventWriter eventWriter) {
        this.toolChatClient = toolChatClient;
        this.ragChatClient = ragChatClient;
        this.agentRoutingService = agentRoutingService;
        this.agentOrchestratorService = agentOrchestratorService;
        this.agentEvaluatorOptimizerService = agentEvaluatorOptimizerService;
        this.chatSessionsService = chatSessionsService;
        this.chatAssistantTurnService = chatAssistantTurnService;
        this.chatMemory = chatMemory;
        this.workspaceUtil = workspaceUtil;
        this.workspacePromptBuilder = workspacePromptBuilder;
        this.eventWriter = eventWriter;
    }

    /**
     * @param format       {@code plain}（默认）或 {@code ndjson}
     * @param kbId         可选；覆盖或补充会话绑定的知识库
     * @param agentMode    {@code route}|{@code orchestrate}；缺省 orchestrate
     * @param qualityLoop  是否在写盘后跑任务内质量环；缺省 true（显式 false 可关）
     * @param criteria     质量环评价标准（可选）
     */
    @RequestMapping(value = "/chat")
    public Flux<String> chat(
            @RequestParam("prompt") String prompt,
            @RequestParam("chatId") String chatId,
            @RequestParam(value = "files", required = false) List<MultipartFile> files,
            @RequestParam(value = "format", required = false) String format,
            @RequestParam(value = "kbId", required = false) String kbId,
            @RequestParam(value = "agentMode", required = false) String agentMode,
            @RequestParam(value = "qualityLoop", required = false) Boolean qualityLoop,
            @RequestParam(value = "criteria", required = false) String criteria,
            HttpServletResponse response) {

        boolean ndjson = "ndjson".equalsIgnoreCase(format);
        if (ndjson) {
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType("application/x-ndjson;charset=UTF-8");
        } else {
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType("text/html;charset=UTF-8");
        }

        bindWorkspace(chatId);
        String effectiveKbId = resolveKbId(chatId, kbId);
        // 默认多步编排；仅显式 agentMode=route 回退单次 Routing
        String mode = normalizeAgentMode(agentMode);
        // 默认开启质量环；仅显式 qualityLoop=false 关闭
        boolean ql = !Boolean.FALSE.equals(qualityLoop);

        // 图片暂不支持：前后端双拒，避免静默丢图
        if (containsImageFile(files)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "暂不支持图片上传，请使用 txt/md/pdf 文本附件");
        }
        // 本轮推理用全文；Memory/气泡只用文件名列表+原问（避免刷新后铺开正文）
        String agentInput = enrichPromptWithUploadedDocuments(prompt, files);
        String memoryUserText = buildMemoryUserText(prompt, files);
        String docSystemAppendix = buildUploadedDocSystemAppendix(files);

        Flux<String> body;
        if (ndjson && AGENT_MODE_ORCHESTRATE.equals(mode)) {
            // 多步编排仅走 ndjson（产品入口）；plain 仍保持单次 Routing
            // maxSteps 不对外暴露，由 AgentOrchestratorService.DEFAULT_MAX_STEPS 生效
            body = orchestrateNdjson(agentInput, memoryUserText, prompt, chatId, effectiveKbId, ql, criteria);
        } else {
            body = ndjson
                    ? textChatNdjson(memoryUserText, docSystemAppendix, prompt, chatId, effectiveKbId, ql, criteria)
                    : textChatPlain(memoryUserText, docSystemAppendix, prompt, chatId, effectiveKbId);
        }
        return body.doFinally(signalType -> WorkspaceContext.clear());
    }

    /** 缺省 / 未知值 → orchestrate；仅显式 {@code route} 关闭多步编排。 */
    private static String normalizeAgentMode(String agentMode) {
        if (AGENT_MODE_ROUTE.equalsIgnoreCase(agentMode)) {
            return AGENT_MODE_ROUTE;
        }
        return AGENT_MODE_ORCHESTRATE;
    }

    private String resolveKbId(String chatId, String requestKbId) {
        if (StringUtils.hasText(requestKbId)) {
            return requestKbId.trim();
        }
        String sessionKb = chatSessionsService.getKbId(chatId);
        return StringUtils.hasText(sessionKb) ? sessionKb.trim() : null;
    }

    private void bindWorkspace(String chatId) {
        String workDir = chatSessionsService.getWorkDir(chatId);
        if (workDir != null) {
            WorkspaceContext.set(workDir);
            log.info("会话 {} 工作目录已设置为: {}", chatId, workDir);
        } else {
            String defaultRoot = workspaceUtil.getWorkspaceRoot().toString();
            WorkspaceContext.set(defaultRoot);
            log.info("会话 {} 使用默认工作目录: {}", chatId, defaultRoot);
        }
    }

    // -------------------------------------------------------------------------
    // plain：与改造前语义一致，仍做 Routing 分发（无 NDJSON 事件）
    // -------------------------------------------------------------------------

    /**
     * @param memoryUserText    写入 ChatMemory 的短用户文案
     * @param docSystemAppendix 上传文档正文（仅本轮 system，避免 Memory 存全文）
     * @param classifyPrompt    分类用原问（不含附件正文）
     */
    private Flux<String> textChatPlain(
            String memoryUserText,
            String docSystemAppendix,
            String classifyPrompt,
            String chatId,
            String kbId) {
        RoutingWorkflow.RoutingResponse classified =
                agentRoutingService.classify(classifyPrompt, kbId, WorkspaceContext.get());
        log.info("plain Routing: route={}, reasoning={}", classified.selection(), classified.reasoning());
        return streamByRoutePlain(classified.selection(), memoryUserText, docSystemAppendix, chatId, kbId);
    }

    private Flux<String> streamByRoutePlain(
            String route,
            String memoryUserText,
            String docSystemAppendix,
            String chatId,
            String kbId) {
        String appendix = docSystemAppendix != null ? docSystemAppendix : "";
        return switch (route) {
            case "kb" -> {
                var spec = ragChatClient.prompt()
                        .advisors(agentRoutingService.buildKbAdvisor(kbId))
                        .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, chatId));
                if (StringUtils.hasText(appendix)) {
                    spec = spec.system("请结合以下用户上传文档与知识库检索结果回答。\n" + appendix);
                }
                yield spec.user(memoryUserText)
                        .stream()
                        .chatResponse()
                        .map(this::toThinkingResponse);
            }
            case "search" -> toolChatClient.prompt()
                    .system(SEARCH_SYSTEM_PROMPT + appendix)
                    .user(memoryUserText)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, chatId))
                    .stream()
                    .chatResponse()
                    .map(this::toThinkingResponse);
            case "file" -> toolChatClient.prompt()
                    .system(buildWorkspaceSystemPrompt() + appendix)
                    .user(memoryUserText)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, chatId))
                    .stream()
                    .chatResponse()
                    .map(this::toThinkingResponse);
            default -> ragChatClient.prompt()
                    .system(GENERAL_SYSTEM_PROMPT + appendix)
                    .user(memoryUserText)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, chatId))
                    .stream()
                    .chatResponse()
                    .map(this::toThinkingResponse);
        };
    }

    // -------------------------------------------------------------------------
    // ndjson：Routing 事件 + 旁路观测
    // -------------------------------------------------------------------------

    /**
     * @param memoryUserText    ChatMemory USER（短）
     * @param docSystemAppendix 上传正文（本轮 system）
     * @param classifyPrompt    分类用原问
     */
    private Flux<String> textChatNdjson(
            String memoryUserText,
            String docSystemAppendix,
            String classifyPrompt,
            String chatId,
            String kbId,
            boolean qualityLoop,
            String criteria) {
        String turnId = chatId + "-" + UUID.randomUUID();
        AtomicInteger seq = new AtomicInteger(0);
        Sinks.Many<ChatStreamEvent> sink = Sinks.many().replay().limit(1024);
        List<ChatStreamEvent> accumulated = Collections.synchronizedList(new ArrayList<>());
        ObservabilityStreamAdvisor obs =
                new ObservabilityStreamAdvisor(turnId, seq, sink, eventWriter.getObjectMapper(), accumulated);

        // 分类用原问，避免附件正文干扰路由
        RoutingWorkflow.RoutingResponse classified =
                agentRoutingService.classify(classifyPrompt, kbId, WorkspaceContext.get());
        log.info("ndjson Routing: route={}, reasoning={}",
                classified.selection(), classified.reasoning());
        emitTracked(sink, accumulated, ChatStreamEvent.route(
                turnId, seq, classified.selection(), classified.reasoning()));

        String workDir = WorkspaceContext.get();
        Mono<Void> drive = streamByRouteNdjson(
                classified.selection(), memoryUserText, docSystemAppendix,
                chatId, kbId, obs, sink, turnId, seq, accumulated)
                .then(Mono.defer(() -> runQualityLoopIfNeeded(
                        qualityLoop, criteria, classifyPrompt, workDir, null, accumulated,
                        sink, turnId, seq)))
                .doOnError(e -> emitError(sink, turnId, seq, e, accumulated))
                .doFinally(signal -> completeSink(sink, turnId, seq, signal, chatId, accumulated));

        return mergeNdjson(sink, drive);
    }

    /**
     * 主路 Orchestrator：route=orchestrate → 逐步 step → 最终 text_delta（可选质量环）。
     *
     * @param agentInput     含附件正文的编排输入
     * @param memoryUserText 写入 spring_ai_chat_memory 的短 USER
     * @param originalPrompt 用户原问（质量环 goal 等）
     */
    private Flux<String> orchestrateNdjson(
            String agentInput,
            String memoryUserText,
            String originalPrompt,
            String chatId,
            String kbId,
            boolean qualityLoop,
            String criteria) {
        String turnId = chatId + "-" + UUID.randomUUID();
        AtomicInteger seq = new AtomicInteger(0);
        Sinks.Many<ChatStreamEvent> sink = Sinks.many().replay().limit(1024);
        List<ChatStreamEvent> accumulated = Collections.synchronizedList(new ArrayList<>());

        emitTracked(sink, accumulated, ChatStreamEvent.route(
                turnId, seq, AGENT_MODE_ORCHESTRATE,
                "主聊天默认多步编排（Orchestrator-Workers），跨能力 Worker 接力"));

        String workDir = WorkspaceContext.get();
        // 编排路径不挂 MessageChatMemoryAdvisor(chatId)：决策前注入会话 Memory，供追问/指代消解
        String dialogueHistory = formatDialogueHistoryForOrchestrator(chatMemory.get(chatId));
        OrchestrateRequest request = new OrchestrateRequest();
        request.setInput(agentInput);
        request.setKbId(kbId);
        request.setWorkDir(workDir);
        // 不 setMaxSteps：服务端 DEFAULT_MAX_STEPS=6（钳制 [1,8]）
        request.setDialogueHistory(dialogueHistory);

        Mono<Void> drive = Mono.fromCallable(() -> agentOrchestratorService.orchestrate(
                        request,
                        step -> emitOrchestrateStep(sink, accumulated, turnId, seq, step)))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(result -> {
                    if (result != null && StringUtils.hasText(result.getFinalAnswer())) {
                        emitTracked(sink, accumulated, ChatStreamEvent.textDelta(
                                turnId, seq, result.getFinalAnswer()));
                    }
                })
                .flatMap(result -> runQualityLoopIfNeeded(
                        qualityLoop, criteria, originalPrompt, workDir,
                        result != null ? result.getSteps() : null,
                        accumulated, sink, turnId, seq))
                .doOnError(e -> emitError(sink, turnId, seq, e, accumulated))
                .doFinally(signal -> {
                    // Orchestrator Worker 使用 orch-* 临时 conversationId，不会写入会话 chatId。
                    // 回合结束时显式落库短 USER+ASSISTANT（不含附件正文）。
                    if (signal == SignalType.ON_COMPLETE) {
                        persistOrchestrateExchange(chatId, memoryUserText, accumulated);
                    }
                    completeSink(sink, turnId, seq, signal, chatId, accumulated);
                })
                .then();

        return mergeNdjson(sink, drive);
    }

    /**
     * 将会话 Memory 格式化为编排器可读的「近期对话」文本（截断防爆上下文）。
     */
    private static String formatDialogueHistoryForOrchestrator(List<Message> memory) {
        if (memory == null || memory.isEmpty()) {
            return null;
        }
        final int maxMessages = 12;
        final int maxChars = 6000;
        int from = Math.max(0, memory.size() - maxMessages);
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < memory.size(); i++) {
            Message m = memory.get(i);
            if (m == null) {
                continue;
            }
            MessageType type = m.getMessageType();
            if (type != MessageType.USER && type != MessageType.ASSISTANT) {
                continue;
            }
            String role = type == MessageType.USER ? "用户" : "助手";
            String text = m.getText() != null ? m.getText().trim() : "";
            if (text.isEmpty()) {
                continue;
            }
            if (text.length() > 800) {
                text = text.substring(0, 800) + "…";
            }
            sb.append(role).append("：").append(text).append('\n');
            if (sb.length() >= maxChars) {
                sb.setLength(maxChars);
                sb.append("\n…（更早内容已省略）");
                break;
            }
        }
        return sb.isEmpty() ? null : sb.toString().trim();
    }

    /**
     * 将本轮用户提问与助手最终正文写入会话级 ChatMemory（spring_ai_chat_memory.conversation_id = chatId）。
     * <p>
     * 与 Routing 路径不同：编排不经 MessageChatMemoryAdvisor(chatId)，必须手动 add。
     */
    private void persistOrchestrateExchange(String chatId, String userPrompt, List<ChatStreamEvent> events) {
        if (!StringUtils.hasText(chatId) || !StringUtils.hasText(userPrompt) || events == null) {
            return;
        }
        StringBuilder assistant = new StringBuilder();
        for (ChatStreamEvent e : events) {
            if (e != null
                    && ChatStreamEvent.TYPE_TEXT_DELTA.equals(e.type())
                    && StringUtils.hasText(e.text())) {
                assistant.append(e.text());
            }
        }
        if (assistant.isEmpty()) {
            return;
        }
        try {
            chatMemory.add(chatId, List.of(
                    new UserMessage(userPrompt),
                    new AssistantMessage(assistant.toString())));
        } catch (Exception e) {
            log.error("编排回合写入会话 Memory 失败 chatId={}: {}", chatId, e.getMessage(), e);
        }
    }

    private void emitOrchestrateStep(
            Sinks.Many<ChatStreamEvent> sink,
            List<ChatStreamEvent> accumulated,
            String turnId,
            AtomicInteger seq,
            OrchestrateStepVO step) {
        if (step == null) {
            return;
        }
        emitTracked(sink, accumulated, ChatStreamEvent.step(
                turnId,
                seq,
                step.getIndex(),
                step.getAction(),
                step.getReasoning(),
                step.getInstruction(),
                step.getObservation()));
    }

    /**
     * qualityLoop 门控：能解析出 write 相对路径才跑 Evaluator-Optimizer；否则跳过。
     */
    private Mono<Void> runQualityLoopIfNeeded(
            boolean qualityLoop,
            String criteria,
            String userGoal,
            String workDir,
            List<OrchestrateStepVO> orchSteps,
            List<ChatStreamEvent> accumulated,
            Sinks.Many<ChatStreamEvent> sink,
            String turnId,
            AtomicInteger seq) {
        if (!qualityLoop) {
            return Mono.empty();
        }
        return Mono.fromRunnable(() -> {
                    String path = WritePathExtractor.fromToolEvents(accumulated);
                    if (!StringUtils.hasText(path) && orchSteps != null) {
                        path = WritePathExtractor.fromOrchestrateSteps(orchSteps);
                    }
                    if (!StringUtils.hasText(path)) {
                        path = WritePathExtractor.hintFromText(userGoal);
                    }
                    if (!StringUtils.hasText(path)) {
                        log.warn("qualityLoop=true 但未解析到 write 路径，已跳过质量环 turnId={}", turnId);
                        return;
                    }

                    EvaluateOptimizeRequest eoReq = new EvaluateOptimizeRequest();
                    eoReq.setGoal(userGoal);
                    eoReq.setPath(path);
                    eoReq.setCriteria(StringUtils.hasText(criteria) ? criteria.trim() : DEFAULT_QUALITY_CRITERIA);
                    eoReq.setWorkDir(StringUtils.hasText(workDir)
                            ? workDir
                            : workspaceUtil.getWorkspaceRoot().toString());

                    log.info("主聊天质量环启动 path={} turnId={}", path, turnId);
                    EvaluateOptimizeResultVO eoResult = agentEvaluatorOptimizerService.evaluateOptimize(eoReq);
                    emitQualityLoopSteps(sink, accumulated, turnId, seq, eoResult);
                    if (eoResult != null) {
                        String summary = "\n\n（写盘质量环："
                                + (eoResult.isPassed() ? "已通过" : "未完全通过")
                                + "，原因=" + eoResult.getFinishedReason()
                                + "，path=" + eoResult.getPath() + "）";
                        emitTracked(sink, accumulated, ChatStreamEvent.textDelta(turnId, seq, summary));
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(e -> {
                    log.warn("质量环执行失败 turnId={}: {}", turnId, e.getMessage());
                    emitTracked(sink, accumulated, ChatStreamEvent.step(
                            turnId, seq, 0, "evaluate_optimize",
                            "质量环异常", e.getMessage() != null ? e.getMessage() : "error", null));
                    return Mono.empty();
                })
                .then();
    }

    private void emitQualityLoopSteps(
            Sinks.Many<ChatStreamEvent> sink,
            List<ChatStreamEvent> accumulated,
            String turnId,
            AtomicInteger seq,
            EvaluateOptimizeResultVO eoResult) {
        if (eoResult == null || eoResult.getRounds() == null) {
            return;
        }
        int i = 1;
        for (EvaluateOptimizeRoundVO round : eoResult.getRounds()) {
            String reasoning = round.getReasoning() != null ? round.getReasoning() : "";
            String feedback = round.getFeedback() != null ? round.getFeedback() : "";
            String obs = "evaluationPass=" + round.isEvaluationPass()
                    + ", ruleCheckPassed=" + round.isRuleCheckPassed()
                    + (StringUtils.hasText(feedback) ? "\n" + feedback : "");
            emitTracked(sink, accumulated, ChatStreamEvent.step(
                    turnId, seq, i++, "evaluate_optimize", reasoning,
                    "iteration=" + round.getIteration(), obs));
        }
    }

    private Mono<Void> streamByRouteNdjson(
            String route,
            String memoryUserText,
            String docSystemAppendix,
            String chatId,
            String kbId,
            ObservabilityStreamAdvisor obs,
            Sinks.Many<ChatStreamEvent> sink,
            String turnId,
            AtomicInteger seq,
            List<ChatStreamEvent> accumulated) {

        String appendix = docSystemAppendix != null ? docSystemAppendix : "";
        Flux<ChatResponse> responses = switch (route) {
            case "kb" -> {
                var spec = ragChatClient.prompt()
                        .advisors(agentRoutingService.buildKbAdvisor(kbId))
                        .advisors(obs)
                        .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, chatId));
                if (StringUtils.hasText(appendix)) {
                    spec = spec.system("请结合以下用户上传文档与知识库检索结果回答。\n" + appendix);
                }
                yield spec.user(memoryUserText)
                        .stream()
                        .chatResponse();
            }
            case "search" -> toolChatClient.prompt()
                    .system(SEARCH_SYSTEM_PROMPT + appendix)
                    .user(memoryUserText)
                    .advisors(obs)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, chatId))
                    .stream()
                    .chatResponse();
            case "file" -> toolChatClient.prompt()
                    .system(buildWorkspaceSystemPrompt() + appendix)
                    .user(memoryUserText)
                    .advisors(obs)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, chatId))
                    .stream()
                    .chatResponse();
            default -> ragChatClient.prompt()
                    .system(GENERAL_SYSTEM_PROMPT + appendix)
                    .user(memoryUserText)
                    .advisors(obs)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, chatId))
                    .stream()
                    .chatResponse();
        };

        return responses
                .doOnNext(cr -> emitTextEvents(sink, turnId, seq, cr, accumulated))
                .then();
    }

    private Flux<String> mergeNdjson(Sinks.Many<ChatStreamEvent> sink, Mono<Void> drive) {
        return Flux.merge(
                sink.asFlux().map(eventWriter::toLine),
                drive.thenMany(Flux.empty())
        );
    }

    private void emitTracked(Sinks.Many<ChatStreamEvent> sink,
                             List<ChatStreamEvent> accumulated,
                             ChatStreamEvent event) {
        accumulated.add(event);
        sink.tryEmitNext(event);
    }

    private void emitTextEvents(Sinks.Many<ChatStreamEvent> sink,
                                String turnId,
                                AtomicInteger seq,
                                ChatResponse response,
                                List<ChatStreamEvent> accumulated) {
        if (response == null || response.getResult() == null) {
            return;
        }
        if (response.hasToolCalls()) {
            return;
        }
        var metadata = response.getResult().getMetadata();
        String thinking = metadata != null
                ? (String) metadata.getOrDefault("reasoningContent", null)
                : null;
        if (thinking != null && !thinking.isEmpty()) {
            emitTracked(sink, accumulated, ChatStreamEvent.thinkingDelta(turnId, seq, thinking));
        }
        String content = response.getResult().getOutput().getText();
        if (content != null && !content.isEmpty()) {
            emitTracked(sink, accumulated, ChatStreamEvent.textDelta(turnId, seq, content));
        }
    }

    private void emitError(Sinks.Many<ChatStreamEvent> sink,
                           String turnId,
                           AtomicInteger seq,
                           Throwable e,
                           List<ChatStreamEvent> accumulated) {
        String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        emitTracked(sink, accumulated, ChatStreamEvent.error(turnId, seq, msg));
    }

    private void completeSink(Sinks.Many<ChatStreamEvent> sink,
                              String turnId,
                              AtomicInteger seq,
                              SignalType signal,
                              String chatId,
                              List<ChatStreamEvent> accumulated) {
        if (signal == SignalType.ON_COMPLETE) {
            emitTracked(sink, accumulated, ChatStreamEvent.done(turnId, seq));
        }
        sink.tryEmitComplete();

        boolean cancelledOrError = signal == SignalType.CANCEL || signal == SignalType.ON_ERROR;
        List<ChatStreamEvent> snapshot = List.copyOf(accumulated);
        Mono.fromRunnable(() -> chatAssistantTurnService.saveTurnFromEvents(
                        chatId, turnId, snapshot, cancelledOrError))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        null,
                        err -> log.error("异步保存助手回合失败 turnId={}", turnId, err)
                );
    }

    // -------------------------------------------------------------------------
    // 聊天附件：文本/PDF 抽取后并入 Agent prompt（非工作区写盘）
    // -------------------------------------------------------------------------

    private static final String GENERAL_SYSTEM_PROMPT =
            "你是友好的助手，用简洁中文回答用户的一般问题。不要尝试调用外部工具。";

    /** 与 Orchestrator search Worker 共用，见 {@link SearchSystemPrompts} */
    private static final String SEARCH_SYSTEM_PROMPT = SearchSystemPrompts.SEARCH;

    private static boolean containsImageFile(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return false;
        }
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            String contentType = file.getContentType();
            if (contentType != null && contentType.startsWith("image/")) {
                return true;
            }
            String name = file.getOriginalFilename();
            if (name != null) {
                String lower = name.toLowerCase();
                if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                        || lower.endsWith(".gif") || lower.endsWith(".webp") || lower.endsWith(".bmp")) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 将上传的 txt/md/pdf 抽成文本，拼进本轮用户目标，供 Orchestrator 本轮推理使用（不落 Memory）。
     */
    private String enrichPromptWithUploadedDocuments(String prompt, List<MultipartFile> files) {
        List<MultipartFile> documents = nonEmptyDocuments(files);
        if (documents.isEmpty()) {
            return prompt;
        }
        String fileList = buildFileList(documents);
        String docContent = extractDocContent(documents);
        StringBuilder sb = new StringBuilder();
        if (!fileList.isEmpty()) {
            sb.append(fileList).append("\n\n");
        }
        if (!docContent.isEmpty()) {
            sb.append("以下为上传文档正文（供回答参考）：\n").append(docContent).append("\n");
        }
        sb.append("用户的问题：\n").append(prompt != null ? prompt : "");
        return sb.toString();
    }

    /**
     * 写入 spring_ai_chat_memory / 刷新后气泡：仅文件名列表 + 原问，不含正文。
     */
    private String buildMemoryUserText(String prompt, List<MultipartFile> files) {
        List<MultipartFile> documents = nonEmptyDocuments(files);
        if (documents.isEmpty()) {
            return prompt != null ? prompt : "";
        }
        String fileList = buildFileList(documents);
        return fileList + "\n\n用户的问题：\n" + (prompt != null ? prompt : "");
    }

    /** Routing 本轮 system 附录：文档正文（MessageChatMemoryAdvisor 主要落 USER，避免气泡铺全文）。 */
    private String buildUploadedDocSystemAppendix(List<MultipartFile> files) {
        List<MultipartFile> documents = nonEmptyDocuments(files);
        if (documents.isEmpty()) {
            return "";
        }
        String docContent = extractDocContent(documents);
        if (!StringUtils.hasText(docContent)) {
            return "";
        }
        return "\n\n以下为上传文档正文（供回答参考）：\n" + docContent;
    }

    private static List<MultipartFile> nonEmptyDocuments(List<MultipartFile> files) {
        List<MultipartFile> documents = new ArrayList<>();
        if (files == null) {
            return documents;
        }
        for (MultipartFile file : files) {
            if (file != null && !file.isEmpty()) {
                documents.add(file);
            }
        }
        return documents;
    }

    private String buildFileList(List<MultipartFile> documents) {
        if (documents.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("上传了以下文件：\n");
        for (MultipartFile file : documents) {
            String name = file.getOriginalFilename();
            if (name == null) continue;
            sb.append("- ").append(name).append("（").append(formatFileSize(file.getSize())).append("）\n");
        }
        return sb.toString().trim();
    }

    private String extractDocContent(List<MultipartFile> documents) {
        if (documents.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        for (MultipartFile file : documents) {
            String filename = file.getOriginalFilename();
            if (filename == null) continue;
            try {
                String text;
                if (filename.toLowerCase().endsWith(".pdf")) {
                    text = extractPdfText(file);
                } else {
                    text = new String(file.getBytes(), StandardCharsets.UTF_8);
                }
                if (!text.isBlank()) {
                    sb.append("\n--- ").append(filename).append(" ---\n");
                    if (text.length() > 50_000) {
                        text = text.substring(0, 50_000) + "\n\n... [内容过长已截断]";
                    }
                    sb.append(text).append("\n");
                }
            } catch (Exception e) {
                log.warn("提取文档内容失败: {}", filename, e);
            }
        }
        return sb.toString();
    }

    private String extractPdfText(MultipartFile file) throws Exception {
        try (PDDocument doc = PDDocument.load(file.getInputStream())) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(doc);
        }
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private String toThinkingResponse(ChatResponse response) {
        String content = response.getResult().getOutput().getText();
        var metadata = response.getResult().getMetadata();
        String thinking = (String) metadata.getOrDefault("reasoningContent", null);
        StringBuilder sb = new StringBuilder();
        if (thinking != null && !thinking.isEmpty()) {
            sb.append("[THINKING]").append(thinking).append("[/THINKING]");
        }
        if (content != null && !content.isEmpty()) {
            sb.append(content);
        }
        return sb.toString();
    }

    /** 路径规则 + 浅层目录摘要（委托 {@link WorkspacePromptBuilder}） */
    private String buildWorkspaceSystemPrompt() {
        return workspacePromptBuilder.build();
    }
}
