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

/** PAPER_TRADER：决策落库与下单写工具（风控在工具内）。 */
@Component
public class PaperTraderActionHandler implements AgentActionHandler {

    private final ProfileToolCapability capability;

    public PaperTraderActionHandler(ProfileToolCapability capability) {
        this.capability = capability;
    }

    @Override
    public AgentAction action() {
        return new AgentAction(
                "paper_trade_review",
                "读取模拟交易上下文，落库决策并可执行下单",
                "{\"instruction\":\"string\"}",
                "trade_decision_batch",
                // 虚拟盘异步轮不可等人批；闸门在工具内，与搜集员 REVERSIBLE_WRITE 同级
                AgentAction.SideEffectLevel.REVERSIBLE_WRITE);
    }

    @Override
    public AgentStepResult handle(
            AgentRunRequest request, AgentDecision decision, int stepIndex) {
        String message = ActionPromptSupport.message(request, decision) + """

                【输出约束】
                优先 recordAiDecisions(runId, decisionsJson)，再对需下单的决策 placePaperOrder(decisionId)。
                同时可在观察中输出 TradeDecisionBatch JSON：{"decisions":[...]} 供兜底。
                reasoning≤80字；evidenceRefs 为短字符串数组（禁止对象）。
                完整批次只出现在本动作结果/工具中，不要依赖 Planner finalOutput。
                """;
        String result = capability.execute(
                AgentScenarioId.PAPER_TRADER,
                request,
                message,
                Set.of("paper-decision", "paper-decision-write", "paper-trade-exec",
                        "paper-run", "paper-market", "paper-strategy"));
        return new AgentStepResult(
                stepIndex, action().id(), AgentStepResult.Status.SUCCEEDED,
                result, null,
                Map.of("toolCalls", 1, "toolName", "paper-trader"));
    }
}
