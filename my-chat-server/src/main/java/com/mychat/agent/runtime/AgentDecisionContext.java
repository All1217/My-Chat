package com.mychat.agent.runtime;

import com.mychat.agent.api.AgentRunRequest;
import com.mychat.agent.api.AgentScenarioProfile;
import com.mychat.agent.api.AgentStepResult;

import java.util.List;

/** 决策器可见的不可变运行状态。 */
public record AgentDecisionContext(
        AgentRunRequest request,
        AgentScenarioProfile profile,
        List<AgentStepResult> completedSteps,
        int nextStepIndex,
        long elapsedMs) {

    /** 固化步骤列表。 */
    public AgentDecisionContext {
        completedSteps = completedSteps == null ? List.of() : List.copyOf(completedSteps);
    }
}
