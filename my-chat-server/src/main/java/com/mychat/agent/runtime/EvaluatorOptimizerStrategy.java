package com.mychat.agent.runtime;

import org.springframework.stereotype.Component;

/** 由 Planner 根据评价观察决定继续优化或完成的策略。 */
@Component
public class EvaluatorOptimizerStrategy extends AbstractLoopStrategy {

    /** 注入通用循环。 */
    public EvaluatorOptimizerStrategy(AgentLoop loop, DecisionEngine decisionEngine) {
        super(loop, decisionEngine);
    }

    /** 策略 ID。 */
    @Override
    public String id() {
        return "evaluator-optimizer";
    }
}
