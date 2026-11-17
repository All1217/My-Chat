package com.mychat.agent.runtime;

import com.mychat.agent.api.AgentRunContext;
import com.mychat.agent.api.AgentRunRequest;
import com.mychat.agent.api.AgentScenarioId;
import com.mychat.agent.api.AgentStepResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** PAPER 回答策略优先动作观察。 */
class PaperBatchAnswerPolicyTest {

    @Test
    void prefersPreferredActionObservationOverFinalOutput() {
        PaperBatchAnswerPolicy policy =
                new PaperBatchAnswerPolicy("paper-research", Set.of("paper_research"));
        AgentRunRequest request = new AgentRunRequest(
                "in",
                new AgentRunContext(
                        "run-1", AgentScenarioId.PAPER_RESEARCH, null,
                        null, null, null, null, Map.of()),
                Map.of());
        AgentDecision decision = new AgentDecision(
                "", "", Map.of(), true,
                "{\"cards\":[{\"symbol\":\"BAD\"}]}", "done");
        List<AgentStepResult> steps = List.of(
                new AgentStepResult(
                        1, "paper_research", AgentStepResult.Status.SUCCEEDED,
                        "{\"cards\":[{\"symbol\":\"GOOD\"}]}", null, Map.of()));
        assertEquals("{\"cards\":[{\"symbol\":\"GOOD\"}]}",
                policy.answer(request, decision, steps));
    }
}
