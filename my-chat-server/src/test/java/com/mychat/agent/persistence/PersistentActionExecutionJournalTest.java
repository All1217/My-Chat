package com.mychat.agent.persistence;

import com.mychat.agent.api.AgentAction;
import com.mychat.agent.api.AgentRunContext;
import com.mychat.agent.api.AgentRunRequest;
import com.mychat.agent.api.AgentScenarioId;
import com.mychat.agent.runtime.ActionExecutionJournal;
import com.mychat.agent.runtime.AgentDecision;
import com.mychat.entity.po.AgentRunStep;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 写动作幂等防重测试。 */
class PersistentActionExecutionJournalTest {

    /** 相同幂等键仍处于 RUNNING 时必须阻止重复执行。 */
    @Test
    void shouldBlockUncertainRunningWrite() {
        AgentRunRepository repository = mock(AgentRunRepository.class);
        AgentRunStep running = step("RUNNING");
        when(repository.findByIdempotencyKey("run-1", "same-key")).thenReturn(running);
        PersistentActionExecutionJournal journal = new PersistentActionExecutionJournal(repository);

        ActionExecutionJournal.Preparation preparation = journal.prepare(
                request(),
                AgentDecision.action("write", "write once",
                        Map.of("idempotencyKey", "same-key")),
                writeAction(),
                1);

        assertThat(preparation.proceed()).isFalse();
        assertThat(preparation.blockedReason()).contains("阻止重复执行");
    }

    /** 已成功的相同写动作直接复用持久化结果。 */
    @Test
    void shouldReplayCompletedWrite() {
        AgentRunRepository repository = mock(AgentRunRepository.class);
        AgentRunStep succeeded = step("SUCCEEDED");
        succeeded.setOutputSummary("saved");
        succeeded.setMetadata(Map.of("toolCalls", 1));
        when(repository.findByIdempotencyKey("run-1", "same-key")).thenReturn(succeeded);
        PersistentActionExecutionJournal journal = new PersistentActionExecutionJournal(repository);

        ActionExecutionJournal.Preparation preparation = journal.prepare(
                request(),
                AgentDecision.action("write", "write once",
                        Map.of("idempotencyKey", "same-key")),
                writeAction(),
                1);

        assertThat(preparation.replayResult()).isNotNull();
        assertThat(preparation.replayResult().observation()).isEqualTo("saved");
        assertThat(preparation.replayResult().metadata()).containsEntry("replayed", true);
    }

    /** 创建测试 Step。 */
    private AgentRunStep step(String status) {
        AgentRunStep step = new AgentRunStep();
        step.setStatus(status);
        return step;
    }

    /** 创建测试请求。 */
    private AgentRunRequest request() {
        AgentRunContext context = new AgentRunContext(
                "run-1", AgentScenarioId.MAIN_CHAT, "chat", "user",
                null, null, null, Map.of());
        return new AgentRunRequest("goal", context, Map.of());
    }

    /** 创建可逆写动作。 */
    private AgentAction writeAction() {
        return new AgentAction("write", "write", "{}", "text",
                AgentAction.SideEffectLevel.REVERSIBLE_WRITE);
    }
}
