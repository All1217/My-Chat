package com.mychat.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 基于知识库的 RAG 流式对话端点
 * 与 /ai/normalChat/chat 的区别：
 *   - 多一个 kbId 参数，用于过滤向量检索范围
 *   - 检索结果拼入上下文后交给 ChatClient 回答
 */
@Slf4j
@RestController
@RequestMapping("/ai/ragChat")
@RequiredArgsConstructor
public class RagChatController {

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;
    private final VectorStore vectorStore;

    @RequestMapping(value = "/chat", produces = "text/html;charset=utf-8")
    public Flux<String> chat(
            @RequestParam("prompt") String prompt,
            @RequestParam("chatId") String chatId,
            @RequestParam("kbId") String kbId) {

        // 1. 向量检索：按 kbId 过滤，只查指定知识库
        SearchRequest request = SearchRequest.builder()
                .query(prompt)
                .topK(5)
                .similarityThreshold(0.5)
                .filterExpression("kbId == '" + kbId + "'")
                .build();
        List<Document> relevantDocs = vectorStore.similaritySearch(request);

        // 2. 拼接命中片段作为上下文
        String context = relevantDocs.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n---\n\n"));

        // 3. 组装 RAG prompt，无命中时走纯对话
        String ragPrompt = context.isBlank()
                ? prompt
                : "基于以下参考内容回答用户问题。如果参考内容不足以回答，请如实告知。\n\n"
                + "参考内容：\n" + context + "\n\n"
                + "用户问题：" + prompt;

        // 4. 流式调用 ChatClient，保持与 ChatController 一致的 thinking 标签处理
        return chatClient.prompt()
                .user(ragPrompt)
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
