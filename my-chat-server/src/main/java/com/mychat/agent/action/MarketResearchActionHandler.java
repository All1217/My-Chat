package com.mychat.agent.action;

import com.mychat.agent.api.AgentAction;
import com.mychat.agent.api.AgentRunRequest;
import com.mychat.agent.api.AgentStepResult;
import com.mychat.agent.capability.WebResearchCapability;
import com.mychat.agent.runtime.AgentActionHandler;
import com.mychat.agent.runtime.AgentDecision;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** MARKET_ANALYSIS 的并行网页研究动作，不依赖 SearchWorker。 */
@Component
public class MarketResearchActionHandler implements AgentActionHandler {

    private final WebResearchCapability capability;

    /** 注入网页研究能力。 */
    public MarketResearchActionHandler(WebResearchCapability capability) {
        this.capability = capability;
    }

    /** 返回只读市场研究动作描述。 */
    @Override
    public AgentAction action() {
        return new AgentAction(
                "market_research",
                "并行检索多个市场研究主题并返回证据",
                "{\"queries\":[\"string\"]}",
                "research_evidence",
                AgentAction.SideEffectLevel.READ_ONLY);
    }

    /** 执行 Planner 给出的并行研究主题。 */
    @Override
    public AgentStepResult handle(
            AgentRunRequest request, AgentDecision decision, int stepIndex) {
        List<String> queries = queries(decision);
        WebResearchCapability.ResearchResult result = capability.research(queries);
        return new AgentStepResult(
                stepIndex,
                action().id(),
                AgentStepResult.Status.SUCCEEDED,
                result.render(),
                null,
                Map.of(
                        "toolCalls", result.queries().size(),
                        "toolName", "web-research",
                        "parallelism", result.queries().size()));
    }

    /** 从结构化参数读取查询；缺失时使用动作指令。 */
    private List<String> queries(AgentDecision decision) {
        Object raw = decision.parameters().get("queries");
        List<String> values = new ArrayList<>();
        if (raw instanceof Iterable<?> iterable) {
            for (Object value : iterable) {
                if (value != null && StringUtils.hasText(String.valueOf(value))) {
                    values.add(String.valueOf(value));
                }
            }
        }
        if (values.isEmpty() && StringUtils.hasText(decision.instruction())) {
            values.add(decision.instruction());
        }
        return values;
    }
}
