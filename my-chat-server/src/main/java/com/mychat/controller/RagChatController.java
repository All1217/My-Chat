package com.mychat.controller;

import com.mychat.config.WorkspaceContext;
import com.mychat.service.ChatSessionsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * 基于知识库的 RAG 流式对话端点
 * 使用 Spring AI 2.0 推荐的 QuestionAnswerAdvisor 机制：
 * 框架自动完成向量检索 → 上下文注入 → 模型回答，
 * 无需手动拼接 prompt，如官方示例 AdvisorRagService 所示。
 * 通过 filterExpression 按 kbId 过滤，实现知识库隔离。
 */
@Slf4j
@RestController
@RequestMapping("/ai/ragChat")
public class RagChatController {
    @Autowired
    private ChatClient ragChatClient;
    @Autowired
    private VectorStore vectorStore;
    @Autowired
    private ChatSessionsService chatSessionsService;

    @RequestMapping(value = "/chat", produces = "text/html;charset=utf-8")
    public Flux<String> chat(
            @RequestParam("prompt") String prompt,
            @RequestParam("chatId") String chatId,
            @RequestParam("kbId") String kbId) {

        // 根据会话ID设置线程级工作目录上下文
        String workDir = chatSessionsService.getWorkDir(chatId);
        if (workDir != null) {
            WorkspaceContext.set(workDir);
        }

        // 每个请求创建独立的 Advisor，携带当前知识库的过滤条件
        QuestionAnswerAdvisor qaAdvisor = QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(SearchRequest.builder()
                        .topK(5)
                        .similarityThreshold(0.5)
                        .filterExpression("kbId == '" + kbId + "'")
                        .build())
                .build();

        // Advisor 自动：向量检索 → 上下文注入 → 生成基于知识库的回答
        return ragChatClient.prompt()
                .advisors(qaAdvisor)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, chatId))
                .user(prompt)
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
                })
                .doFinally(signalType -> WorkspaceContext.clear());
    }
}
