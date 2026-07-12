package com.mychat.controller;

import com.mychat.config.WorkspaceContext;
import com.mychat.service.ChatSessionsService;
import com.mychat.utils.WorkspaceUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.content.Media;
import org.springframework.util.MimeType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.nio.file.Paths;
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
        // 根据会话ID设置线程级工作目录上下文
        String workDir = chatSessionsService.getWorkDir(chatId);
        if (workDir != null) {
            WorkspaceContext.set(workDir);
            log.info("会话 {} 工作目录已设置为: {}", chatId, workDir);
        } else {
            // 未指定目录的普通对话，使用配置的默认工作区根目录
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
                .map(response -> {
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
                });
    }

    private Flux<String> multiModalChat(String prompt, String chatId, List<MultipartFile> files) {
        // 1.解析多媒体
        List<Media> medias = files.stream()
                .map(file -> new Media(
                                MimeType.valueOf(Objects.requireNonNull(file.getContentType())),
                                file.getResource()
                        )
                )
                .toList();
        // 2.请求模型
        return toolChatClient.prompt()
                .system(buildWorkspaceSystemPrompt())
                .user(prompt)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, chatId))
                .stream()
                .chatResponse()
                .map(response -> {
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
                });
    }

    /**
     * 构建当前请求的 workspace 感知系统提示。
     * chat() 中已确保 WorkspaceContext 始终被设置（DB 值或默认根目录）。
     */
    private String buildWorkspaceSystemPrompt() {
        String workDir = WorkspaceContext.get();
        String name = Paths.get(workDir).getFileName().toString();
        return String.format("""
                所有涉及文件的查看、创建、写入、修改、删除、重命名、复制操作，都必须通过可用工具实际执行。
                你绝不能在回复中假装已经完成了文件操作。
                
                当前工作目录: %1$s
                路径规则：所有路径都是相对于当前工作目录的**相对路径**。
                不要再把工作目录名 "%2$s" 作为路径前缀。
                ✅ 正确: path="src/components/App.vue"
                ✅ 正确: path="README.md"
                ❌ 错误: path="%2$s/src/components/App.vue"
                ❌ 错误: path="%2$s/README.md"
                """, workDir, name);
    }
}
