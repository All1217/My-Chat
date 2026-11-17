package com.mychat.agent.runtime;

import com.mychat.agent.api.AgentRunRequest;
import com.mychat.agent.api.AgentRunResult;
import com.mychat.agent.api.AgentScenarioProfile;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 按显式策略 ID 分发工作流。 */
@Component
public class WorkflowRunner {

    private final Map<String, WorkflowStrategy> strategies;

    /** 注册并校验策略名唯一。 */
    public WorkflowRunner(List<WorkflowStrategy> strategies) {
        Map<String, WorkflowStrategy> indexed = new LinkedHashMap<>();
        for (WorkflowStrategy strategy : strategies) {
            if (indexed.putIfAbsent(strategy.id(), strategy) != null) {
                throw new IllegalStateException("Duplicate workflow strategy: " + strategy.id());
            }
        }
        this.strategies = Map.copyOf(indexed);
    }

    /** 使用指定策略执行。 */
    public AgentRunResult run(String strategyId, AgentRunRequest request, AgentScenarioProfile profile) {
        WorkflowStrategy strategy = strategies.get(strategyId);
        if (strategy == null) {
            throw new IllegalArgumentException("Unknown workflow strategy: " + strategyId);
        }
        return strategy.run(request, profile);
    }
}
