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

/** PAPER_RESEARCH 的只读证据收集动作。 */
@Component
public class PaperResearchActionHandler implements AgentActionHandler {

    private final ProfileToolCapability capability;

    /** 注入受限 Profile 工具能力。 */
    public PaperResearchActionHandler(ProfileToolCapability capability) {
        this.capability = capability;
    }

    /** 返回只读搜集动作。 */
    @Override
    public AgentAction action() {
        return new AgentAction(
                "paper_research",
                "读取搜集轮次并按需进行受限网页研究",
                "{\"instruction\":\"string\"}",
                "research_card_batch",
                AgentAction.SideEffectLevel.READ_ONLY);
    }

    /** 执行只读研究，不注册卡片写入或交易工具。 */
    @Override
    public AgentStepResult handle(
            AgentRunRequest request, AgentDecision decision, int stepIndex) {
        String result = capability.execute(
                AgentScenarioId.PAPER_RESEARCH,
                request,
                ActionPromptSupport.message(request, decision),
                Set.of("paper-run", "web-search"));
        return new AgentStepResult(
                stepIndex, action().id(), AgentStepResult.Status.SUCCEEDED,
                result, null,
                Map.of("toolCalls", 1, "toolName", "paper-research-readonly"));
    }
}
