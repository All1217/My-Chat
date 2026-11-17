package com.mychat.agent.runtime;

import com.mychat.agent.api.AgentRunRequest;
import com.mychat.agent.api.AgentRunResult;
import com.mychat.agent.api.AgentScenarioProfile;

/** 复用 AgentLoop 的基础策略。 */
abstract class AbstractLoopStrategy implements WorkflowStrategy {

    private final AgentLoop loop;
    private final DecisionEngine decisionEngine;

    /** 注入循环和决策器。 */
    protected AbstractLoopStrategy(AgentLoop loop, DecisionEngine decisionEngine) {
        this.loop = loop;
        this.decisionEngine = decisionEngine;
    }

    /** 使用策略决策器运行通用循环。 */
    @Override
    public AgentRunResult run(AgentRunRequest request, AgentScenarioProfile profile) {
        return loop.run(request, profile, decisionEngine);
    }
}
