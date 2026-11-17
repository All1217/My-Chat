package com.mychat.agent.runtime;

import com.mychat.agent.api.AgentRunRequest;
import com.mychat.agent.api.AgentStepResult;

import java.util.List;

/** 将终止决策与步骤观察转换为最终输出。 */
public interface AnswerPolicy {

    /** 策略 ID，对应 AgentScenarioProfile.answerPolicy。 */
    String id();

    /** 生成最终输出。 */
    String answer(AgentRunRequest request, AgentDecision finishDecision, List<AgentStepResult> steps);
}
