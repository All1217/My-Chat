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

/** PAPER_WAREHOUSE_ANALYST：深搜后调价值等级。 */
@Component
public class PaperWarehouseAnalyzeActionHandler implements AgentActionHandler {

    private final ProfileToolCapability capability;

    public PaperWarehouseAnalyzeActionHandler(ProfileToolCapability capability) {
        this.capability = capability;
    }

    @Override
    public AgentAction action() {
        return new AgentAction(
                "paper_warehouse_analyze",
                "深搜单票并调用 updateValueTier 调整价值等级",
                "{\"instruction\":\"string\"}",
                "text",
                AgentAction.SideEffectLevel.REVERSIBLE_WRITE);
    }

    @Override
    public AgentStepResult handle(
            AgentRunRequest request, AgentDecision decision, int stepIndex) {
        String message = ActionPromptSupport.message(request, decision) + """

                【输出约束】
                深搜后必须调用 updateValueTier；禁止改成 0。
                工具成功后观察可写短确认；本步结束后应由 Planner finish，勿重复空转深搜。
                """;
        String result = capability.execute(
                AgentScenarioId.PAPER_WAREHOUSE_ANALYST,
                request,
                message,
                Set.of("paper-market", "paper-research-feed", "paper-strategy",
                        "paper-note", "web-search"));
        return new AgentStepResult(
                stepIndex, action().id(), AgentStepResult.Status.SUCCEEDED,
                result, null,
                Map.of("toolCalls", 1, "toolName", "paper-warehouse-analyze"));
    }
}
