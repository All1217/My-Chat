package com.mychat.agent.runtime;

/** 为 AgentLoop 决定下一动作；实现可由 LLM、固定路由或测试 Fake 提供。 */
@FunctionalInterface
public interface DecisionEngine {

    /** 根据当前状态产出下一步。 */
    AgentDecision decide(AgentDecisionContext context);
}
