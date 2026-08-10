package com.mychat.controller;

import com.mychat.service.AgentEvaluatorOptimizerService;
import com.mychat.service.AgentOrchestratorService;
import com.mychat.entity.dto.EvaluateOptimizeRequest;
import com.mychat.entity.dto.OrchestrateRequest;
import com.mychat.common.ChatStreamEvent;
import com.mychat.utils.ChatStreamEventWriter;
import com.mychat.config.WorkspaceContext;
import com.mychat.service.ChatAssistantTurnService;
import com.mychat.service.ChatSessionsService;
import com.mychat.utils.WorkspaceUtil;
import com.mychat.utils.WritePathExtractor;
import com.mychat.vo.EvaluateOptimizeResultVO;
import com.mychat.vo.EvaluateOptimizeRoundVO;
import com.mychat.vo.OrchestrateResultVO;
import com.mychat.vo.OrchestrateStepVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
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
 * 主聊天流式端点（仅 Orchestrator + 写盘质量环）。
 * <p>
 * 产品入口必须 {@code format=ndjson}：事件含 {@code route=orchestrate} / {@code step} /
 * 最终 {@code text_delta} 等。<br>
 * 单次 Routing 调试见 {@code POST /ai/agent/route}（Demo，不进入本控制器）。<br>
 * 可选 {@code kbId}：请求参数优先，否则读会话绑定。<br>
 * 附件仅支持 txt/md/pdf（抽文本后并入 Agent）；图片暂不支持。
 */
@Slf4j
@RestController
@RequestMapping("/ai/normalChat")
public class ChatController {

    private static final String AGENT_MODE_ORCHESTRATE = "orchestrate";
    private static final String DEFAULT_QUALITY_CRITERIA =
            "文件存在、非空，且内容符合用户目标。";

    private final AgentOrchestratorService agentOrchestratorService;
    private final AgentEvaluatorOptimizerService agentEvaluatorOptimizerService;
    private final ChatSessionsService chatSessionsService;
    private final ChatAssistantTurnService chatAssistantTurnService;
    private final ChatMemory chatMemory;
    private final WorkspaceUtil workspaceUtil;
    private final ChatStreamEventWriter eventWriter;

    public ChatController(
            AgentOrchestratorService agentOrchestratorService,
            AgentEvaluatorOptimizerService agentEvaluatorOptimizerService,
            ChatSessionsService chatSessionsService,
            ChatAssistantTurnService chatAssistantTurnService,
            ChatMemory chatMemory,
            WorkspaceUtil workspaceUtil,
            ChatStreamEventWriter eventWriter) {
        this.agentOrchestratorService = agentOrchestratorService;
        this.agentEvaluatorOptimizerService = agentEvaluatorOptimizerService;
        this.chatSessionsService = chatSessionsService;
        this.chatAssistantTurnService = chatAssistantTurnService;
        this.chatMemory = chatMemory;
        this.workspaceUtil = workspaceUtil;
        this.eventWriter = eventWriter;
    }

    /**
     * @param format       必须为 {@code ndjson}
     * @param kbId         可选；覆盖或补充会话绑定的知识库
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
            @RequestParam(value = "qualityLoop", required = false) Boolean qualityLoop,
            @RequestParam(value = "criteria", required = false) String criteria,
            HttpServletResponse response) {

        if (!"ndjson".equalsIgnoreCase(format)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "主聊天仅支持 format=ndjson（Orchestrator）；单次 Routing 请用 POST /ai/agent/route");
        }
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/x-ndjson;charset=UTF-8");

        bindWorkspace(chatId);
        String effectiveKbId = resolveKbId(chatId, kbId);
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

        // maxSteps 不对外暴露，由 AgentOrchestratorService.DEFAULT_MAX_STEPS 生效
        Flux<String> body = orchestrateNdjson(
                agentInput, memoryUserText, prompt, chatId, effectiveKbId, ql, criteria);
        return body.doFinally(signalType -> WorkspaceContext.clear());
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
    // ndjson：Orchestrator
    // -------------------------------------------------------------------------

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

        // 编排 sync → 最终答复 token 流式 text_delta → 可选质量环 → 落库
        Mono<Void> drive = Mono.fromCallable(() -> agentOrchestratorService.orchestrate(
                        request,
                        step -> emitOrchestrateStep(sink, accumulated, turnId, seq, step)))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(result -> streamOrchestrateFinalAnswer(
                                result, originalPrompt, sink, turnId, seq, accumulated)
                        .then(Mono.defer(() -> runQualityLoopIfNeeded(
                                qualityLoop, criteria, originalPrompt, workDir,
                                result != null ? result.getSteps() : null,
                                accumulated, sink, turnId, seq))))
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
     * 将 {@link AgentOrchestratorService#streamFinalAnswer} 的增量写入 NDJSON {@code text_delta}。
     * 若流式无产出，回退为一次推送 sync {@code finalAnswer}（保证气泡非空）。
     */
    private Mono<Void> streamOrchestrateFinalAnswer(
            OrchestrateResultVO result,
            String userGoal,
            Sinks.Many<ChatStreamEvent> sink,
            String turnId,
            AtomicInteger seq,
            List<ChatStreamEvent> accumulated) {
        if (result == null) {
            return Mono.empty();
        }
        StringBuilder streamed = new StringBuilder();
        return agentOrchestratorService.streamFinalAnswer(userGoal, result)
                .doOnNext(delta -> {
                    streamed.append(delta);
                    emitTracked(sink, accumulated, ChatStreamEvent.textDelta(turnId, seq, delta));
                })
                .then(Mono.fromRunnable(() -> {
                    if (streamed.isEmpty() && StringUtils.hasText(result.getFinalAnswer())) {
                        log.warn("streamFinalAnswer 无增量，回退整段 finalAnswer turnId={}", turnId);
                        emitTracked(sink, accumulated, ChatStreamEvent.textDelta(
                                turnId, seq, result.getFinalAnswer()));
                    }
                }));
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

}
