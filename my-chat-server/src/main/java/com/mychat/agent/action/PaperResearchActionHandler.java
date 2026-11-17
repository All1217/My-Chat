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
        String message = ActionPromptSupport.message(request, decision) + """

                【输出约束】
                最终只输出紧凑 ResearchCardBatch JSON：{"cards":[...]}，不要 Markdown。
                story≤80字；risks/signalTags/evidenceRefs 均为短字符串数组（evidenceRefs 禁止对象）。
                完整批次只出现在本动作结果中，不要依赖 Planner finalOutput。
                """;
        String result = capability.execute(
                AgentScenarioId.PAPER_RESEARCH,
                request,
                message,
                Set.of("paper-market", "paper-research-feed", "paper-strategy",
                        "web-search", "mcp-crawler"));
        return new AgentStepResult(
                stepIndex, action().id(), AgentStepResult.Status.SUCCEEDED,
                result, null,
                Map.of("toolCalls", 1, "toolName", "paper-research-readonly"));
    }
}

