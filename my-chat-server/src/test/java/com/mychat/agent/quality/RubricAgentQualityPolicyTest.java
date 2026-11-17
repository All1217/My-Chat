package com.mychat.agent.quality;

import com.mychat.agent.api.AgentRunContext;
import com.mychat.agent.api.AgentRunRequest;
import com.mychat.agent.api.AgentRunResult;
import com.mychat.agent.api.AgentScenarioId;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** rubric evaluator 门控测试。 */
class RubricAgentQualityPolicyTest {

    /** explicit-rubric 缺少明确标准时跳过评价。 */
    @Test
    void shouldSkipWhenExplicitRubricIsMissing() {
        RubricAgentQualityPolicy policy = new RubricAgentQualityPolicy(
                "explicit-rubric", "", mock(ChatClient.class));
        AgentRunRequest request = new AgentRunRequest(
                "goal",
                new AgentRunContext(
                        "run-1", AgentScenarioId.MARKET_ANALYSIS, "chat-1",
                        null, null, null, null, Map.of()),
                Map.of());
        AgentRunResult result = new AgentRunResult(
                AgentRunResult.Status.SUCCEEDED, "answer", List.of(), null, Map.of());

        AgentRunResult evaluated = policy.evaluate(request, result);

        assertThat(evaluated.metrics())
                .containsEntry("qualityPolicy", "explicit-rubric")
                .containsEntry("qualitySkipped", true);
    }
}
