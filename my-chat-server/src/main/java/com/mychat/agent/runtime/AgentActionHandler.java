package com.mychat.agent.runtime;

import com.mychat.agent.api.AgentAction;
import com.mychat.agent.api.AgentRunRequest;
import com.mychat.agent.api.AgentStepResult;

/** 可注册动作的执行端口。 */
public interface AgentActionHandler {

    /** 动作描述。 */
    AgentAction action();

    /** 执行单步并返回结构化观察。 */
    AgentStepResult handle(AgentRunRequest request, AgentDecision decision, int stepIndex);
}
