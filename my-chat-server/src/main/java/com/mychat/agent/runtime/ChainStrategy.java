package com.mychat.agent.runtime;

import org.springframework.stereotype.Component;

/** 顺序执行 Planner 生成动作的 Chain 策略。 */
@Component
public class ChainStrategy extends AbstractLoopStrategy {

    /** 注入通用循环。 */
    public ChainStrategy(AgentLoop loop, DecisionEngine decisionEngine) {
        super(loop, decisionEngine);
    }

    /** 策略 ID。 */
    @Override
    public String id() {
        return "chain";
    }
}
