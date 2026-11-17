package com.mychat.agent.runtime;

import com.mychat.agent.api.AgentRunRequest;
import com.mychat.agent.api.AgentRunResult;
import com.mychat.agent.api.AgentScenarioId;
import com.mychat.agent.api.AgentScenarioProfile;
import com.mychat.agent.context.ScenarioContextFactory;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 多场景入口的 Profile 驱动测试。 */
class ScenarioAgentServiceTest {

    /** 新场景只需注册 Profile，即可通过同一 Runtime 执行并携带 businessRef。 */
    @Test
    void shouldBuildExplicitContextAndUseSharedRuntime() {
        AgentRuntime runtime = mock(AgentRuntime.class);
        ScenarioProfileRegistry profiles = mock(ScenarioProfileRegistry.class);
        AgentScenarioProfile profile = new AgentScenarioProfile(
                AgentScenarioId.MARKET_ANALYSIS,
                "test",
                Set.of("market_research"),
                Set.of("web-search"),
                4,
                4,
                "market_analysis",
                "market-evidence",
                "orchestrator-workers",
                "ephemeral",
                "market-analysis",
                "explicit-rubric");
        when(profiles.require(AgentScenarioId.MARKET_ANALYSIS)).thenReturn(profile);
        when(runtime.run(org.mockito.ArgumentMatchers.any())).thenReturn(
                new AgentRunResult(
                        AgentRunResult.Status.SUCCEEDED, "ok", List.of(), null, Map.of()));
        ScenarioAgentService service = new ScenarioAgentService(
                runtime, profiles, new ScenarioContextFactory());

        service.run(new ScenarioAgentService.ScenarioRunCommand(
                AgentScenarioId.MARKET_ANALYSIS,
                "分析市场",
                "market-chat",
                null,
                "job-1",
                null,
                "证据可追溯",
                Map.of()));

        ArgumentCaptor<AgentRunRequest> captor = ArgumentCaptor.forClass(AgentRunRequest.class);
        verify(runtime).run(captor.capture());
        AgentRunRequest request = captor.getValue();
        assertThat(request.context().businessRef()).isEqualTo("job-1");
        assertThat(request.context().metadata())
                .containsEntry("contextPolicy", "market-analysis")
                .containsEntry("qualityPolicy", "explicit-rubric");
        assertThat(request.parameters().get("contextText").toString())
                .contains("证据可追溯", "分析市场");
    }
}
