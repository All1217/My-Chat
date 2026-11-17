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

/** PAPER_TRADE_EVALUATOR：复盘并输出 StageEvalBatch。 */
@Component
public class PaperTradeEvaluateActionHandler implements AgentActionHandler {

    private final ProfileToolCapability capability;

    public PaperTradeEvaluateActionHandler(ProfileToolCapability capability) {
        this.capability = capability;
    }

    @Override
    public AgentAction action() {
        return new AgentAction(
                "paper_trade_evaluate",
                "复盘阶段表现并给出可编辑策略修改建议",
                "{\"instruction\":\"string\"}",
                "stage_eval_batch",
                AgentAction.SideEffectLevel.READ_ONLY);
    }

    @Override
    public AgentStepResult handle(
            AgentRunRequest request, AgentDecision decision, int stepIndex) {
        String message = ActionPromptSupport.message(request, decision) + """

                【输出约束】
                最终只输出紧凑 StageEvalBatch JSON：
                {"summaryText":"...","strategyAdvice":"...","emailBody":"...","evidenceRefs":[]}
                strategyAdvice / emailBody 只能针对可编辑风格与偏好，禁止建议改锁定风控、value_tier、整手规则。
                完整 JSON 只出现在本动作观察中，不要依赖 Planner finalOutput。
                """;
        String result = capability.execute(
                AgentScenarioId.PAPER_TRADE_EVALUATOR,
                request,
                message,
                Set.of("paper-strategy", "paper-decision", "paper-run", "paper-note",
                        "paper-market", "paper-research-feed", "mcp-crawler", "web-search"));
        return new AgentStepResult(
                stepIndex, action().id(), AgentStepResult.Status.SUCCEEDED,
                result, null,
                Map.of("toolCalls", 1, "toolName", "paper-trade-evaluate"));
    }
}
