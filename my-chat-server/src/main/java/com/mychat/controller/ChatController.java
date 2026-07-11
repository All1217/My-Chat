package com.mychat.controller;

import com.mychat.config.WorkspaceContext;
import com.mychat.service.ChatSessionsService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.content.Media;
import org.springframework.util.MimeType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Objects;

@RequiredArgsConstructor
@RestController
@RequestMapping("/ai/normalChat")
public class ChatController {
    private final ChatMemory chatMemory;
    private final ChatClient toolChatClient;
    private final ChatSessionsService chatSessionsService;

    @RequestMapping(value = "/chat", produces = "text/html;charset=utf-8")
    public Flux<String> chat(
            @RequestParam("prompt") String prompt,
            @RequestParam("chatId") String chatId,
            @RequestParam(value = "files", required = false) List<MultipartFile> files) {
        // 根据会话ID设置线程级工作目录上下文（ShellTool 从中读取工作目录）
        String workDir = chatSessionsService.getWorkDir(chatId);
        if (workDir != null) {
            WorkspaceContext.set(workDir);
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
}
