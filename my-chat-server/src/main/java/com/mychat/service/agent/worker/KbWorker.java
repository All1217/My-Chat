package com.mychat.service.agent.worker;

import com.mychat.service.knowledge.KbScope;
import com.mychat.service.knowledge.KnowledgeRetrievalService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * retrieve_kb Worker：按 kbScope 检索或列目录，再交给 ragChatClient 生成。
 */
@Service
public class KbWorker {

    private final ChatClient ragChatClient;
    private final KnowledgeRetrievalService knowledgeRetrievalService;

    public KbWorker(
            @Qualifier("ragChatClient") ChatClient ragChatClient,
            KnowledgeRetrievalService knowledgeRetrievalService) {
        this.ragChatClient = ragChatClient;
        this.knowledgeRetrievalService = knowledgeRetrievalService;
    }

    /**
     * 执行知识库步。检索 query 用用户原问；临时 conversationId 不写会话 Memory。
     */
    public WorkerOutcome run(
            String userMessage, String kbId, String userGoal, String instruction, String kbScope) {
        String conversationId = "orch-kb-" + UUID.randomUUID();
        // 1. 检索：query=用户原问；范围由编排器 kbScope 决定
        String searchQuery = WorkerPromptSupport.kbSearchQuery(userGoal, instruction);
        KbScope scope = KbScope.from(kbScope);
        KnowledgeRetrievalService.RagContext rag =
                knowledgeRetrievalService.buildRagContext(kbId, searchQuery, scope);
        String prompt = WorkerPromptSupport.buildKbWorkerUserPrompt(userMessage, rag.promptBlock());
        // 2. 生成：临时 conversationId，不写会话 Memory
        String content = ragChatClient.prompt()
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .user(prompt)
                .call()
                .content();
        // 3. 带回 citations，供 step.args 落入气泡引用
        return new WorkerOutcome(content != null ? content : "", rag.citations());
    }
}
