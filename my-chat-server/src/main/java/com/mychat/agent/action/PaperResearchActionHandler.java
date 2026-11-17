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

/** PAPER_RESEARCH：研究证据 + 优先 upsertResearchCards 落库。 */
@Component
public class PaperResearchActionHandler implements AgentActionHandler {

    private final ProfileToolCapability capability;

    public PaperResearchActionHandler(ProfileToolCapability capability) {
        this.capability = capability;
    }

    @Override
    public AgentAction action() {
        return new AgentAction(
                "paper_research",
                "读取搜集材料并落库研究卡片",
                "{\"instruction\":\"string\"}",
                "research_card_batch",
                AgentAction.SideEffectLevel.REVERSIBLE_WRITE);
    }

    @Override
    public AgentStepResult handle(
            AgentRunRequest request, AgentDecision decision, int stepIndex) {
        String message = ActionPromptSupport.message(request, decision) + """

                【输出约束】
                优先调用 upsertResearchCards(runId, cardsJson) 落库；同时可在观察中输出
                ResearchCardBatch JSON：{"cards":[...]} 供兜底。
                story≤80字；risks/signalTags/evidenceRefs 均为短字符串数组（evidenceRefs 禁止对象）。
                完整批次只出现在本动作结果/工具中，不要依赖 Planner finalOutput。
                """;
        String result = capability.execute(
                AgentScenarioId.PAPER_RESEARCH,
                request,
                message,
                Set.of("paper-market", "paper-research-feed", "paper-strategy",
                        "paper-note-batch", "web-search", "mcp-crawler"));
        return new AgentStepResult(
                stepIndex, action().id(), AgentStepResult.Status.SUCCEEDED,
                result, null,
                Map.of("toolCalls", 1, "toolName", "paper-research"));
    }
}
