package com.mychat.agent.capability;

import com.mychat.agent.api.AgentRunResult;
import com.mychat.agent.api.AgentScenarioId;
import com.mychat.agent.runtime.ScenarioAgentService;
import org.springframework.stereotype.Component;

import java.util.Map;

/** 面向业务服务的 MARKET_ANALYSIS Runtime 门面。 */
@Component
public class MarketAnalysisCapability {

    private final ScenarioAgentService scenarioAgentService;

    /** 注入多场景 Runtime 入口。 */
    public MarketAnalysisCapability(ScenarioAgentService scenarioAgentService) {
        this.scenarioAgentService = scenarioAgentService;
    }

    /** 执行带明确证据 rubric 的市场分析。 */
    public String analyze(String input, String businessRef) {
        AgentRunResult result = scenarioAgentService.run(
                new ScenarioAgentService.ScenarioRunCommand(
                        AgentScenarioId.MARKET_ANALYSIS,
                        input,
                        businessRef,
                        null,
                        businessRef,
                        null,
                        "结论与数值必须可追溯到网页证据；资料不足时不得猜测。",
                        Map.of()));
        if (result.status() != AgentRunResult.Status.SUCCEEDED) {
            throw new IllegalStateException(
                    "市场分析 Agent 失败: " + result.status() + " " + result.errorMessage());
        }
        return result.output();
    }
}
