package com.mychat.agent.action;

import com.mychat.agent.api.AgentAction;
import com.mychat.agent.api.AgentRunRequest;
import com.mychat.agent.api.AgentScenarioId;
import com.mychat.agent.api.AgentStepResult;
import com.mychat.agent.capability.ProfileToolCapability;
import com.mychat.agent.runtime.AgentActionHandler;
import com.mychat.agent.runtime.AgentDecision;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/** PAPER_TRADER 的只读账户与历史决策分析动作。 */
@Component
public class PaperTraderActionHandler implements AgentActionHandler {

    private final ProfileToolCapability capability;

    /** 注入受限 Profile 工具能力。 */
    public PaperTraderActionHandler(ProfileToolCapability capability) {
        this.capability = capability;
    }

    /** 返回无交易执行权的分析动作。 */
    @Override
    public AgentAction action() {
        return new AgentAction(
                "paper_trade_review",
                "读取模拟交易历史并生成建议，不执行订单",
                "{\"instruction\":\"string\"}",
                "trade_decision_batch",
                AgentAction.SideEffectLevel.READ_ONLY);
    }

    /** 执行只读交易复核，不暴露订单和账户写工具。 */
    @Override
    public AgentStepResult handle(
            AgentRunRequest request, AgentDecision decision, int stepIndex) {
        String result = capability.execute(
                AgentScenarioId.PAPER_TRADER,
                request,
                ActionPromptSupport.message(request, decision),
                Set.of("paper-decision", "paper-run"));
        return new AgentStepResult(
                stepIndex, action().id(), AgentStepResult.Status.SUCCEEDED,
                result, null,
                Map.of("toolCalls", 1, "toolName", "paper-trader-readonly"));
    }
}
