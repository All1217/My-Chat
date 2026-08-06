package com.mychat.controller;

import com.mychat.service.AgentRoutingService;
import com.mychat.common.RoutingWorkflow;
import com.mychat.utils.ObservabilityStreamAdvisor;
import com.mychat.common.ChatStreamEvent;
import com.mychat.utils.ChatStreamEventWriter;
import com.mychat.config.WorkspaceContext;
import com.mychat.service.ChatAssistantTurnService;
import com.mychat.service.ChatSessionsService;
import com.mychat.utils.WorkspaceUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.util.StringUtils;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.util.MimeType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 主聊天流式端点（含 Routing Workflow 分发）。
 * <p>
 * {@code format} 缺省 / {@code plain}：保持历史 {@code text/html} 行为。<br>
 * {@code format=ndjson}：结构化事件流（含 {@code route} + 工具观测）。
 * <p>
 * 可选 {@code kbId}：请求参数优先，否则读会话绑定；供分类器约束与 kb 路由使用。
 */
@Slf4j
@RestController
@RequestMapping("/ai/normalChat")
public class ChatController {
    private final ChatClient toolChatClient;
    private final ChatClient ragChatClient;
    private final AgentRoutingService agentRoutingService;
    private final ChatSessionsService chatSessionsService;
    private final ChatAssistantTurnService chatAssistantTurnService;
    private final WorkspaceUtil workspaceUtil;
    private final ChatStreamEventWriter eventWriter;

    public ChatController(
            @Qualifier("toolChatClient") ChatClient toolChatClient,
            @Qualifier("ragChatClient") ChatClient ragChatClient,
            AgentRoutingService agentRoutingService,
            ChatSessionsService chatSessionsService,
            ChatAssistantTurnService chatAssistantTurnService,
            WorkspaceUtil workspaceUtil,
            ChatStreamEventWriter eventWriter) {
        this.toolChatClient = toolChatClient;
        this.ragChatClient = ragChatClient;
        this.agentRoutingService = agentRoutingService;
        this.chatSessionsService = chatSessionsService;
        this.chatAssistantTurnService = chatAssistantTurnService;
        this.workspaceUtil = workspaceUtil;
        this.eventWriter = eventWriter;
    }

    /**
     * @param format {@code plain}（默认）或 {@code ndjson}
     * @param kbId   可选；覆盖或补充会话绑定的知识库
     */
    @RequestMapping(value = "/chat")
    public Flux<String> chat(
            @RequestParam("prompt") String prompt,
            @RequestParam("chatId") String chatId,
            @RequestParam(value = "files", required = false) List<MultipartFile> files,
            @RequestParam(value = "format", required = false) String format,
            @RequestParam(value = "kbId", required = false) String kbId,
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

        Flux<String> body;
        if (files == null || files.isEmpty()) {
            body = ndjson
                    ? textChatNdjson(prompt, chatId, effectiveKbId)
                    : textChatPlain(prompt, chatId, effectiveKbId);
        } else {
            // 带附件：固定走工具路径（file），仍发 route 事件便于时间线
            body = ndjson
                    ? multiModalChatNdjson(prompt, chatId, files)
                    : multiModalChatPlain(prompt, chatId, files);
        }
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
    // plain：与改造前语义一致，仍做 Routing 分发（无 NDJSON 事件）
    // -------------------------------------------------------------------------

    private Flux<String> textChatPlain(String prompt, String chatId, String kbId) {
        RoutingWorkflow.RoutingResponse classified =
                agentRoutingService.classify(prompt, kbId, WorkspaceContext.get());
        log.info("plain Routing: route={}, reasoning={}", classified.selection(), classified.reasoning());
        return streamByRoutePlain(classified.selection(), prompt, chatId, kbId);
    }

    private Flux<String> streamByRoutePlain(String route, String prompt, String chatId, String kbId) {
        return switch (route) {
            case "kb" -> ragChatClient.prompt()
                    .advisors(agentRoutingService.buildKbAdvisor(kbId))
                    .user(prompt)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, chatId))
                    .stream()
                    .chatResponse()
                    .map(this::toThinkingResponse);
            case "search" -> toolChatClient.prompt()
                    .system(SEARCH_SYSTEM_PROMPT)
                    .user(prompt)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, chatId))
                    .stream()
                    .chatResponse()
                    .map(this::toThinkingResponse);
            case "file" -> toolChatClient.prompt()
                    .system(buildWorkspaceSystemPrompt())
                    .user(prompt)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, chatId))
                    .stream()
                    .chatResponse()
                    .map(this::toThinkingResponse);
            default -> ragChatClient.prompt()
                    .system(GENERAL_SYSTEM_PROMPT)
                    .user(prompt)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, chatId))
                    .stream()
                    .chatResponse()
                    .map(this::toThinkingResponse);
        };
    }

    private Flux<String> multiModalChatPlain(String prompt, String chatId, List<MultipartFile> files) {
        PromptParts parts = buildMultiModalParts(prompt, files);
        var spec = toolChatClient.prompt()
                .system(parts.systemMsg())
                .user(u -> {
                    u.text(parts.userPrompt());
                    for (MultipartFile img : parts.images()) {
                        try {
                            u.media(MimeType.valueOf(Objects.requireNonNull(img.getContentType())),
                                    img.getResource());
                        } catch (Exception e) {
                            log.warn("跳过不支持的图片: {}", img.getOriginalFilename());
                        }
                    }
                })
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, chatId));

        return spec.stream().chatResponse()
                .map(this::toThinkingResponse)
                .onErrorResume(e -> {
                    if (isImageUnsupported(e)) {
                        log.warn("模型不支持图片分析: {}", e.getMessage());
                        return Flux.just("当前模型不支持图片分析。");
                    }
                    return Flux.error(e);
                });
    }

    // -------------------------------------------------------------------------
    // ndjson：Routing 事件 + 旁路观测
    // -------------------------------------------------------------------------

    private Flux<String> textChatNdjson(String prompt, String chatId, String kbId) {
        String turnId = chatId + "-" + UUID.randomUUID();
        AtomicInteger seq = new AtomicInteger(0);
        Sinks.Many<ChatStreamEvent> sink = Sinks.many().replay().limit(1024);
        List<ChatStreamEvent> accumulated = Collections.synchronizedList(new ArrayList<>());
        ObservabilityStreamAdvisor obs =
                new ObservabilityStreamAdvisor(turnId, seq, sink, eventWriter.getObjectMapper(), accumulated);

        // 分类在请求线程同步执行，避免 subscribeOn 丢失 WorkspaceContext 传播起点
        RoutingWorkflow.RoutingResponse classified =
                agentRoutingService.classify(prompt, kbId, WorkspaceContext.get());
        log.info("ndjson Routing: route={}, reasoning={}",
                classified.selection(), classified.reasoning());
        emitTracked(sink, accumulated, ChatStreamEvent.route(
                turnId, seq, classified.selection(), classified.reasoning()));

        Mono<Void> drive = streamByRouteNdjson(
                classified.selection(), prompt, chatId, kbId, obs, sink, turnId, seq, accumulated)
                .doOnError(e -> emitError(sink, turnId, seq, e, accumulated))
                .doFinally(signal -> completeSink(sink, turnId, seq, signal, chatId, accumulated));

        return mergeNdjson(sink, drive);
    }

    private Mono<Void> streamByRouteNdjson(
            String route,
            String prompt,
            String chatId,
            String kbId,
            ObservabilityStreamAdvisor obs,
            Sinks.Many<ChatStreamEvent> sink,
            String turnId,
            AtomicInteger seq,
            List<ChatStreamEvent> accumulated) {

        Flux<ChatResponse> responses = switch (route) {
            case "kb" -> ragChatClient.prompt()
                    .advisors(agentRoutingService.buildKbAdvisor(kbId))
                    .advisors(obs)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, chatId))
                    .user(prompt)
                    .stream()
                    .chatResponse();
            case "search" -> toolChatClient.prompt()
                    .system(SEARCH_SYSTEM_PROMPT)
                    .user(prompt)
                    .advisors(obs)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, chatId))
                    .stream()
                    .chatResponse();
            case "file" -> toolChatClient.prompt()
                    .system(buildWorkspaceSystemPrompt())
                    .user(prompt)
                    .advisors(obs)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, chatId))
                    .stream()
                    .chatResponse();
            default -> ragChatClient.prompt()
                    .system(GENERAL_SYSTEM_PROMPT)
                    .user(prompt)
                    .advisors(obs)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, chatId))
                    .stream()
                    .chatResponse();
        };

        return responses
                .doOnNext(cr -> emitTextEvents(sink, turnId, seq, cr, accumulated))
                .then();
    }

    private Flux<String> multiModalChatNdjson(String prompt, String chatId, List<MultipartFile> files) {
        String turnId = chatId + "-" + UUID.randomUUID();
        AtomicInteger seq = new AtomicInteger(0);
        Sinks.Many<ChatStreamEvent> sink = Sinks.many().replay().limit(1024);
        List<ChatStreamEvent> accumulated = Collections.synchronizedList(new ArrayList<>());
        ObservabilityStreamAdvisor obs =
                new ObservabilityStreamAdvisor(turnId, seq, sink, eventWriter.getObjectMapper(), accumulated);

        emitTracked(sink, accumulated, ChatStreamEvent.route(
                turnId, seq, "file", "用户上传了附件，固定走 file / 工具路径"));

        PromptParts parts = buildMultiModalParts(prompt, files);

        Mono<Void> drive = toolChatClient.prompt()
                .system(parts.systemMsg())
                .user(u -> {
                    u.text(parts.userPrompt());
                    for (MultipartFile img : parts.images()) {
                        try {
                            u.media(MimeType.valueOf(Objects.requireNonNull(img.getContentType())),
                                    img.getResource());
                        } catch (Exception e) {
                            log.warn("跳过不支持的图片: {}", img.getOriginalFilename());
                        }
                    }
                })
                .advisors(obs)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, chatId))
                .stream()
                .chatResponse()
                .doOnNext(cr -> emitTextEvents(sink, turnId, seq, cr, accumulated))
                .onErrorResume(e -> {
                    if (isImageUnsupported(e)) {
                        log.warn("模型不支持图片分析: {}", e.getMessage());
                        emitTracked(sink, accumulated, ChatStreamEvent.textDelta(
                                turnId, seq, "当前模型不支持图片分析。"));
                        return Flux.empty();
                    }
                    emitError(sink, turnId, seq, e, accumulated);
                    return Flux.error(e);
                })
                .doFinally(signal -> completeSink(sink, turnId, seq, signal, chatId, accumulated))
                .then();

        return mergeNdjson(sink, drive);
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
    // 多模态公共拼装
    // -------------------------------------------------------------------------

    private static final String GENERAL_SYSTEM_PROMPT =
            "你是友好的助手，用简洁中文回答用户的一般问题。不要尝试调用外部工具。";

    private static final String SEARCH_SYSTEM_PROMPT = """
            当前请求已路由到 search。请优先调用可用的远程 MCP 工具
            （网页搜索、天气查询等）获取信息后再回答；不要编造实时数据。
            """;

    private record PromptParts(String systemMsg, String userPrompt, List<MultipartFile> images) {
    }

    private PromptParts buildMultiModalParts(String prompt, List<MultipartFile> files) {
        List<MultipartFile> images = new ArrayList<>();
        List<MultipartFile> documents = new ArrayList<>();
        for (MultipartFile file : files) {
            String contentType = file.getContentType();
            if (contentType != null && contentType.startsWith("image/")) {
                images.add(file);
            } else {
                documents.add(file);
            }
        }
        String fileList = buildFileList(documents);
        String docContent = extractDocContent(documents);
        String userPrompt = fileList.isEmpty()
                ? prompt
                : fileList + "\n\n用户的问题：\n" + prompt;
        String systemMsg = buildWorkspaceSystemPrompt();
        if (!docContent.isEmpty()) {
            systemMsg += "\n\n用户上传了以下文档内容供参考：\n" + docContent;
        }
        return new PromptParts(systemMsg, userPrompt, images);
    }

    private boolean isImageUnsupported(Throwable e) {
        String msg = e.getMessage() != null ? e.getMessage() : "";
        return msg.contains("400") || msg.contains("unsupported") || msg.contains("not support");
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

    private String buildWorkspaceSystemPrompt() {
        String workDir = WorkspaceContext.get();
        String name = Paths.get(workDir).getFileName().toString();
        return String.format("""
                所有涉及文件的查看、创建、写入、修改、删除、重命名、复制操作，务必积极调用可用工具实际执行。
                不能在回复中假装执行了文件操作。
                当前工作目录: %1$s
                路径规则：所有路径都是相对于当前工作目录的**相对路径**。
                不要把工作目录名 "%2$s" 作为路径前缀。
                ✅ 正确: path="src/components/App.vue"
                ✅ 正确: path="README.md"
                ❌ 错误: path="%2$s/src/components/App.vue"
                ❌ 错误: path="%2$s/README.md"
                """, workDir, name);
    }
}
