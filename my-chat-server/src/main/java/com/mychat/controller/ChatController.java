package com.mychat.controller;

import com.mychat.config.WorkspaceContext;
import com.mychat.service.ChatSessionsService;
import com.mychat.utils.WorkspaceUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.util.MimeType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/ai/normalChat")
public class ChatController {
    private final ChatClient toolChatClient;
    private final ChatSessionsService chatSessionsService;
    private final WorkspaceUtil workspaceUtil;

    @RequestMapping(value = "/chat", produces = "text/html;charset=utf-8")
    public Flux<String> chat(
            @RequestParam("prompt") String prompt,
            @RequestParam("chatId") String chatId,
            @RequestParam(value = "files", required = false) List<MultipartFile> files) {
        String workDir = chatSessionsService.getWorkDir(chatId);
        if (workDir != null) {
            WorkspaceContext.set(workDir);
            log.info("会话 {} 工作目录已设置为: {}", chatId, workDir);
        } else {
            String defaultRoot = workspaceUtil.getWorkspaceRoot().toString();
            WorkspaceContext.set(defaultRoot);
            log.info("会话 {} 使用默认工作目录: {}", chatId, defaultRoot);
        }
        if (files == null || files.isEmpty()) {
            return textChat(prompt, chatId)
                    .doFinally(signalType -> WorkspaceContext.clear());
        } else {
            return multiModalChat(prompt, chatId, files)
                    .doFinally(signalType -> WorkspaceContext.clear());
        }
    }

    private Flux<String> textChat(String prompt, String chatId) {
        return toolChatClient.prompt()
                .system(buildWorkspaceSystemPrompt())
                .user(prompt)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, chatId))
                .stream()
                .chatResponse()
                .map(this::toThinkingResponse);
    }

    /**
     * 多模态对话：按文件类型分叉处理。
     * <ul>
     *   <li>图片 → 作为 Media 对象附加到请求（模型不支持时 Flux.error 兜底）</li>
     *   <li>文档（PDF/文本）→ 全文提取后放入 system prompt（仅供 AI 阅读），user prompt 只保留文件列表</li>
     * </ul>
     * 这样 user prompt 存入 {@code spring_ai_chat_memory} 后，刷新时返回的是简洁的文件列表而非全文。
     */
    private Flux<String> multiModalChat(String prompt, String chatId, List<MultipartFile> files) {
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

        // 1. 文件列表（给用户消息，与前端格式一致，存入 chat_memory）
        String fileList = buildFileList(documents);
        // 2. 文档全文（给 AI 参考，放入 system prompt，不存入 chat_memory）
        String docContent = extractDocContent(documents);

        // 3. user prompt：只含文件列表 + 用户问题 → 存入 chat_memory 后保持刷新前后一致
        String userPrompt = fileList.isEmpty()
                ? prompt
                : fileList + "\n\n用户的问题：\n" + prompt;

        // 4. system prompt：原有工作目录 + 文档全文（仅 AI 可见）
        String systemMsg = buildWorkspaceSystemPrompt();
        if (!docContent.isEmpty()) {
            systemMsg += "\n\n用户上传了以下文档内容供参考：\n" + docContent;
        }

        // 5. 构建请求
        var spec = toolChatClient.prompt()
                .system(systemMsg)
                .user(u -> {
                    u.text(userPrompt);
                    for (MultipartFile img : images) {
                        try {
                            u.media(MimeType.valueOf(Objects.requireNonNull(img.getContentType())),
                                    img.getResource());
                        } catch (Exception e) {
                            log.warn("跳过不支持的图片: {}", img.getOriginalFilename());
                        }
                    }
                })
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, chatId));

        Flux<String> result = spec.stream().chatResponse()
                .map(this::toThinkingResponse);

        // 错误兜底：模型不支持图片时返回友好提示
        return result.onErrorResume(e -> {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("400") || msg.contains("unsupported") || msg.contains("not support")) {
                log.warn("模型不支持图片分析: {}", msg);
                return Flux.just("当前模型不支持图片分析。");
            }
            return Flux.error(e);
        });
    }

    /**
     * 生成简洁的文件列表（与前端格式一致，后端接管后保证刷新前后显示统一）
     * 格式：「上传了以下文件：\n- foo.pdf（2.3MB）」
     */
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

    /** 提取文档全文内容（放入 system prompt，仅 AI 可见） */
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

    /** 使用 PDFBox 提取 PDF 文本 */
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

    /** 统一处理 AI 响应中的 thinking 标签 */
    private String toThinkingResponse(org.springframework.ai.chat.model.ChatResponse response) {
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
