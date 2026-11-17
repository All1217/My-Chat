package com.mychat.agent.runtime;

import org.springframework.stereotype.Component;

/** Planner 按观察动态选择 Worker Action 的编排策略。 */
@Component
public class OrchestratorWorkersStrategy extends AbstractLoopStrategy {

    /** 注入通用循环。 */
    public OrchestratorWorkersStrategy(AgentLoop loop, DecisionEngine decisionEngine) {
        super(loop, decisionEngine);
    }

    /** 策略 ID。 */
    @Override
    public String id() {
        return "orchestrator-workers";
    }
}
