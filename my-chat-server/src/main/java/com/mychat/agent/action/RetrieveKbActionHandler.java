package com.mychat.agent.action;

import com.mychat.agent.api.AgentAction;
import com.mychat.agent.api.AgentRunRequest;
import com.mychat.agent.api.AgentStepResult;
import com.mychat.agent.runtime.AgentActionHandler;
import com.mychat.agent.runtime.AgentDecision;
import com.mychat.service.knowledge.KbScope;
import com.mychat.service.knowledge.KnowledgeRetrievalService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/** 统一知识库检索 Action，保留 scope 与 citations。 */
@Component
public class RetrieveKbActionHandler implements AgentActionHandler {

    private final ChatClient ragChatClient;
    private final KnowledgeRetrievalService retrievalService;

    /** 注入 RAG 客户端和统一检索服务。 */
    public RetrieveKbActionHandler(
            @Qualifier("ragChatClient") ChatClient ragChatClient,
            KnowledgeRetrievalService retrievalService) {
        this.ragChatClient = ragChatClient;
        this.retrievalService = retrievalService;
    }

    /** 返回动作描述。 */
    @Override
    public AgentAction action() {
        return new AgentAction("retrieve_kb", "查询已绑定知识库",
                "{\"instruction\":\"string\",\"kbScope\":\"catalog|vector\"}",
                "text", AgentAction.SideEffectLevel.READ_ONLY);
    }

    /** 复用原检索优先级和 RAG 生成路径。 */
    @Override
    public AgentStepResult handle(AgentRunRequest request, AgentDecision decision, int stepIndex) {
        if (!StringUtils.hasText(request.context().kbId())) {
            throw new IllegalArgumentException("retrieve_kb 需要 kbId");
        }
        String scope = String.valueOf(decision.parameters().getOrDefault("kbScope", "vector"));
        String message = ActionPromptSupport.message(request, decision);
        KnowledgeRetrievalService.RagContext rag = retrievalService.buildRagContext(
                request.context().kbId(), request.input(), KbScope.from(scope));
        String content = ragChatClient.prompt()
                .user(message + "\n\n【知识库材料】\n" + rag.promptBlock())
                .advisors(advisor -> advisor.param(
                        ChatMemory.CONVERSATION_ID,
                        "agent:" + request.context().runId() + ":" + stepIndex))
                .call()
                .content();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("kbScope", scope);
        metadata.put("citations", rag.citations() == null ? java.util.List.of() : rag.citations());
        metadata.put("toolCalls", 1);
        metadata.put("toolName", "knowledge-retrieval");
        return new AgentStepResult(stepIndex, action().id(), AgentStepResult.Status.SUCCEEDED,
                content == null ? "" : content, null, metadata);
    }
}
