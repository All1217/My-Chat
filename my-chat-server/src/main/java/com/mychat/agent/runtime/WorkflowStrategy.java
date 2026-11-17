package com.mychat.agent.runtime;

import com.mychat.agent.api.AgentRunRequest;
import com.mychat.agent.api.AgentRunResult;
import com.mychat.agent.api.AgentScenarioProfile;

/** 可按场景选择的工作流执行策略。 */
public interface WorkflowStrategy {

    /** 策略 ID。 */
    String id();

    /** 执行策略。 */
    AgentRunResult run(AgentRunRequest request, AgentScenarioProfile profile);
}
