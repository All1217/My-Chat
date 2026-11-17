package com.mychat.agent.runtime;

import org.springframework.stereotype.Component;

/** 单次能力路由策略；决策输出仍受 Profile 白名单约束。 */
@Component
public class RoutingStrategy extends AbstractLoopStrategy {

    /** 注入通用循环。 */
    public RoutingStrategy(AgentLoop loop, DecisionEngine decisionEngine) {
        super(loop, decisionEngine);
    }

    /** 策略 ID。 */
    @Override
    public String id() {
        return "routing";
    }
}
