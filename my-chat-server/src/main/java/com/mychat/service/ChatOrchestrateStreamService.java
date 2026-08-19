package com.mychat.service;

import com.mychat.common.ChatStreamEvent;
import com.mychat.config.WorkspaceContext;
import com.mychat.entity.dto.EvaluateOptimizeRequest;
import com.mychat.entity.dto.OrchestrateRequest;
import com.mychat.utils.ChatStreamEventWriter;
import com.mychat.utils.NdjsonStreamSupport;
import com.mychat.utils.WorkspaceUtil;
import com.mychat.utils.WritePathExtractor;
import com.mychat.vo.EvaluateOptimizeResultVO;
import com.mychat.vo.EvaluateOptimizeRoundVO;
import com.mychat.vo.OrchestrateResultVO;
import com.mychat.vo.OrchestrateStepVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 主聊天 Orchestrate NDJSON 管道（自 ChatController 下沉）。
 * <p>
 * 读路径：决策前注入 {@code dialogueHistory}；Worker / 最终流式也注入摘要。<br>
 * 写路径：仅回合 {@code ON_COMPLETE} 时 {@link #persistOrchestrateExchange}；
 * Worker 继续 {@code orch-*}，不挂 MessageChatMemoryAdvisor 写会话 chatId。
 */
@Slf4j
@Service
public class ChatOrchestrateStreamService {

    private static final String AGENT_MODE_ORCHESTRATE = "orchestrate";
    private static final String DEFAULT_QUALITY_CRITERIA =
            "文件存在、非空，且内容符合用户目标。";

    private final AgentOrchestratorService agentOrchestratorService;
    private final AgentEvaluatorOptimizerService agentEvaluatorOptimizerService;
    private final ChatAssistantTurnService chatAssistantTurnService;
    private final ChatMemory chatMemory;
    private final WorkspaceUtil workspaceUtil;
    private final ChatStreamEventWriter eventWriter;
    private final DocumentService documentService;

    public ChatOrchestrateStreamService(
            AgentOrchestratorService agentOrchestratorService,
            AgentEvaluatorOptimizerService agentEvaluatorOptimizerService,
            ChatAssistantTurnService chatAssistantTurnService,
            ChatMemory chatMemory,
            WorkspaceUtil workspaceUtil,
            ChatStreamEventWriter eventWriter,
            DocumentService documentService) {
        this.agentOrchestratorService = agentOrchestratorService;
        this.agentEvaluatorOptimizerService = agentEvaluatorOptimizerService;
        this.chatAssistantTurnService = chatAssistantTurnService;
        this.chatMemory = chatMemory;
        this.workspaceUtil = workspaceUtil;
        this.eventWriter = eventWriter;
        this.documentService = documentService;
    }

    /**
     * 主路：route=orchestrate → step → text_delta → 可选质量环 → Memory/turn 落库。
     *
     * @param agentInput     含附件正文的编排输入
     * @param memoryUserText 写入 spring_ai_chat_memory 的短 USER（仅文件名+原问）
     * @param originalPrompt 用户原问（质量环 goal 等）
     */
    public Flux<String> streamOrchestrate(
            String agentInput,
            String memoryUserText,
            String originalPrompt,
            String chatId,
            String kbId,
            boolean qualityLoop,
            String criteria) {
        String turnId = chatId + "-" + UUID.randomUUID();
        AtomicInteger seq = new AtomicInteger(0);
        // sink=边生成边推给前端的「直播通道」
        Sinks.Many<ChatStreamEvent> sink = Sinks.many().replay().limit(1024);
        // accumulated=本回合事件清单，结束后用来落库/回放
        List<ChatStreamEvent> accumulated = Collections.synchronizedList(new ArrayList<>());
        // 更新事件堆栈
        NdjsonStreamSupport.emitTracked(sink, accumulated, ChatStreamEvent.route(
                turnId, seq, AGENT_MODE_ORCHESTRATE,
                "主聊天默认多步编排（Orchestrator-Workers），跨能力 Worker 接力"));

        String workDir = WorkspaceContext.get();
        // 多轮agent丧失了官方的自动注入历史对话机制，需要手动注入
        // 因此手动从数据库取出历史对话，拼接进提示词
        String dialogueHistory = formatDialogueHistoryForOrchestrator(chatMemory.get(chatId));
        OrchestrateRequest request = new OrchestrateRequest();
        request.setInput(agentInput);
        request.setKbId(kbId);
        request.setWorkDir(workDir);
        request.setDialogueHistory(dialogueHistory);

        Mono<Void> drive = Mono.fromCallable(
                        // 传入一个 Callable ，预约待执行的编排任务
                        () -> agentOrchestratorService.orchestrate(
                                request, step -> emitOrchestrateStep(sink, accumulated, turnId, seq, step))
                )
                .subscribeOn(Schedulers.boundedElastic()) // 使用什么线程池执行任务
                // 规定编排任务执行完后下一步干什么：可能是最终答复 + 回答质量判定
                .flatMap(
                        result -> streamOrchestrateFinalAnswer(result, originalPrompt, dialogueHistory, sink, turnId, seq, accumulated)
                                .then(Mono.defer(() -> runQualityLoopIfNeeded(
                                        qualityLoop, criteria, originalPrompt, workDir,
                                        result != null ? result.getSteps() : null,
                                        accumulated, sink, turnId, seq)
                                ))
                )
                .doOnError(e -> NdjsonStreamSupport.emitError(sink, turnId, seq, e, accumulated))
                .doFinally(signal -> {
                    // 读路径：dialogueHistory 注入决策/Worker/最终流式；Worker 用 orch-*，不写 chatId。
                    // 写路径：回合结束显式落库短 USER+ASSISTANT（不含附件正文）。
                    if (signal == SignalType.ON_COMPLETE) {
                        persistOrchestrateExchange(chatId, memoryUserText, accumulated);
                    }
                    completeSink(sink, turnId, seq, signal, chatId, accumulated);
                })
                .then();

        return NdjsonStreamSupport.mergeNdjson(sink, drive, eventWriter);
    }

    /**
     * 将上传的 txt/md/pdf 抽成文本，拼进本轮用户目标（不落 Memory）。
     */
    public String enrichPromptWithUploadedDocuments(String prompt, List<MultipartFile> files) {
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
    public String buildMemoryUserText(String prompt, List<MultipartFile> files) {
        List<MultipartFile> documents = nonEmptyDocuments(files);
        if (documents.isEmpty()) {
            return prompt != null ? prompt : "";
        }
        String fileList = buildFileList(documents);
        return fileList + "\n\n用户的问题：\n" + (prompt != null ? prompt : "");
    }

    public static boolean containsImageFile(List<MultipartFile> files) {
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

    private Mono<Void> streamOrchestrateFinalAnswer(
            OrchestrateResultVO result,
            String userGoal,
            String dialogueHistory,
            Sinks.Many<ChatStreamEvent> sink,
            String turnId,
            AtomicInteger seq,
            List<ChatStreamEvent> accumulated) {
        if (result == null) {
            return Mono.empty();
        }
        StringBuilder streamed = new StringBuilder();
        return agentOrchestratorService.streamFinalAnswer(userGoal, result, dialogueHistory)
                .doOnNext(delta -> {
                    streamed.append(delta);
                    NdjsonStreamSupport.emitTracked(sink, accumulated,
                            ChatStreamEvent.textDelta(turnId, seq, delta));
                })
                .then(Mono.fromRunnable(() -> {
                    if (streamed.isEmpty() && StringUtils.hasText(result.getFinalAnswer())) {
                        log.warn("streamFinalAnswer 无增量，回退整段 finalAnswer turnId={}", turnId);
                        NdjsonStreamSupport.emitTracked(sink, accumulated, ChatStreamEvent.textDelta(
                                turnId, seq, result.getFinalAnswer()));
                    }
                }));
    }

    static String formatDialogueHistoryForOrchestrator(List<Message> memory) {
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

    private void persistOrchestrateExchange(String chatId, String userPrompt, List<ChatStreamEvent> events) {
        if (!StringUtils.hasText(chatId) || !StringUtils.hasText(userPrompt) || events == null) {
            return;
        }
        StringBuilder assistant = new StringBuilder();
        for (ChatStreamEvent e : events) {
            // 与流式路径一致：保留空格/换行 delta，勿用 hasText 丢掉 Markdown 结构空白
            if (e != null
                    && ChatStreamEvent.TYPE_TEXT_DELTA.equals(e.type())
                    && e.text() != null
                    && !e.text().isEmpty()) {
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
        NdjsonStreamSupport.emitTracked(sink, accumulated, ChatStreamEvent.step(
                turnId,
                seq,
                step.getIndex(),
                step.getAction(),
                step.getReasoning(),
                step.getInstruction(),
                step.getObservation()));
    }

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
                        NdjsonStreamSupport.emitTracked(sink, accumulated,
                                ChatStreamEvent.textDelta(turnId, seq, summary));
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(e -> {
                    log.warn("质量环执行失败 turnId={}: {}", turnId, e.getMessage());
                    NdjsonStreamSupport.emitTracked(sink, accumulated, ChatStreamEvent.step(
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
            NdjsonStreamSupport.emitTracked(sink, accumulated, ChatStreamEvent.step(
                    turnId, seq, i++, "evaluate_optimize", reasoning,
                    "iteration=" + round.getIteration(), obs));
        }
    }

    private void completeSink(
            Sinks.Many<ChatStreamEvent> sink,
            String turnId,
            AtomicInteger seq,
            SignalType signal,
            String chatId,
            List<ChatStreamEvent> accumulated) {
        if (signal == SignalType.ON_COMPLETE) {
            NdjsonStreamSupport.emitTracked(sink, accumulated, ChatStreamEvent.done(turnId, seq));
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

    // 提取文件名列表
    private String buildFileList(List<MultipartFile> documents) {
        if (documents.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("上传了以下文件：\n");
        for (MultipartFile file : documents) {
            String name = file.getOriginalFilename();
            if (name == null) {
                continue;
            }
            sb.append("- ").append(name).append("（").append(formatFileSize(file.getSize())).append("）\n");
        }
        return sb.toString().trim();
    }

    // 提取文件内容
    private String extractDocContent(List<MultipartFile> documents) {
        if (documents.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (MultipartFile file : documents) {
            String filename = file.getOriginalFilename();
            if (filename == null) {
                continue;
            }
            try {
                String text = documentService.extractPlainText(file.getInputStream(), filename);
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

    // 格式化文件大小
    private static String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        }
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
