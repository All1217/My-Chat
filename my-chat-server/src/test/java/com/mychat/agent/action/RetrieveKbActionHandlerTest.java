package com.mychat.agent.action;

import com.mychat.agent.api.AgentRunContext;
import com.mychat.agent.api.AgentRunRequest;
import com.mychat.agent.api.AgentScenarioId;
import com.mychat.agent.api.AgentStepResult;
import com.mychat.agent.runtime.AgentDecision;
import com.mychat.entity.dto.KnowledgeRetrieveHit;
import com.mychat.service.knowledge.KbScope;
import com.mychat.service.knowledge.KnowledgeRetrievalService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** retrieve_kb 新路径兼容性测试。 */
class RetrieveKbActionHandlerTest {

    /** 验证原始问题作为 query，且 scope/citations 原样带回。 */
    @Test
    void shouldPreserveScopeAndCitations() {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        KnowledgeRetrievalService retrieval = mock(KnowledgeRetrievalService.class);
        KnowledgeRetrieveHit citation = new KnowledgeRetrieveHit();
        citation.setFilename("guide.md");
        when(retrieval.buildRagContext("kb-1", "original question", KbScope.CATALOG))
                .thenReturn(new KnowledgeRetrievalService.RagContext(
                        "material", true, 0, List.of(citation)));
        RetrieveKbActionHandler handler = new RetrieveKbActionHandler(chatClient, retrieval);
        AgentRunContext context = new AgentRunContext("run", AgentScenarioId.MAIN_CHAT,
                "conversation", "user", "kb-1", null, null, Map.of());
        AgentRunRequest request = new AgentRunRequest("original question", context, Map.of());

        AgentStepResult result = handler.handle(request,
                AgentDecision.action("retrieve_kb", "sub task", Map.of("kbScope", "catalog")), 1);

        assertThat(result.observation()).isEmpty();
        assertThat(result.metadata().get("kbScope")).isEqualTo("catalog");
        assertThat(result.metadata().get("citations")).isEqualTo(List.of(citation));
        verify(retrieval).buildRagContext(
                "kb-1", "original question", KbScope.CATALOG);
    }
}
