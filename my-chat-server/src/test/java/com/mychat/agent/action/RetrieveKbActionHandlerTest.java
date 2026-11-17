package com.mychat.agent.action;

import com.mychat.agent.api.AgentRunContext;
import com.mychat.agent.api.AgentRunRequest;
import com.mychat.agent.api.AgentScenarioId;
import com.mychat.agent.api.AgentStepResult;
import com.mychat.agent.runtime.AgentDecision;
import com.mychat.entity.dto.KnowledgeRetrieveHit;
import com.mychat.service.agent.worker.KbWorker;
import com.mychat.service.agent.worker.WorkerOutcome;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** retrieve_kb 新路径兼容性测试。 */
class RetrieveKbActionHandlerTest {

    /** 验证 query 优先级由旧 Worker 保持，且 scope/citations 原样带回。 */
    @Test
    void shouldPreserveWorkerScopeAndCitations() {
        KbWorker worker = mock(KbWorker.class);
        KnowledgeRetrieveHit citation = new KnowledgeRetrieveHit();
        citation.setFilename("guide.md");
        when(worker.run(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq("kb-1"),
                org.mockito.ArgumentMatchers.eq("original question"),
                org.mockito.ArgumentMatchers.eq("sub task"), org.mockito.ArgumentMatchers.eq("catalog")))
                .thenReturn(new WorkerOutcome("answer", List.of(citation)));
        RetrieveKbActionHandler handler = new RetrieveKbActionHandler(worker);
        AgentRunContext context = new AgentRunContext("run", AgentScenarioId.MAIN_CHAT,
                "conversation", "user", "kb-1", null, null, Map.of());
        AgentRunRequest request = new AgentRunRequest("original question", context, Map.of());

        AgentStepResult result = handler.handle(request,
                AgentDecision.action("retrieve_kb", "sub task", Map.of("kbScope", "catalog")), 1);

        assertThat(result.observation()).isEqualTo("answer");
        assertThat(result.metadata().get("kbScope")).isEqualTo("catalog");
        assertThat(result.metadata().get("citations")).isEqualTo(List.of(citation));
        verify(worker).run(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq("kb-1"),
                org.mockito.ArgumentMatchers.eq("original question"),
                org.mockito.ArgumentMatchers.eq("sub task"), org.mockito.ArgumentMatchers.eq("catalog"));
    }
}
