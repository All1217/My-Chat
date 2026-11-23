package com.mychat.agent.action;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mychat.agent.api.AgentAction;
import com.mychat.agent.api.AgentRunRequest;
import com.mychat.agent.api.AgentScenarioId;
import com.mychat.agent.api.AgentStepResult;
import com.mychat.agent.capability.ProfileToolCapability;
import com.mychat.agent.runtime.AgentActionHandler;
import com.mychat.agent.runtime.AgentDecision;
import com.mychat.apps.papertrade.agent.PartialJsonSalvage;
import com.mychat.apps.papertrade.agent.TradeDecisionBatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/** PAPER_TRADER：决策落库与下单写工具（风控在工具内）。 */
@Component
public class PaperTraderActionHandler implements AgentActionHandler {

    private static final Logger log = LoggerFactory.getLogger(PaperTraderActionHandler.class);
    private static final int TRANSCRIPT_TAIL = 8000;

    private final ProfileToolCapability capability;
    private final ChatClient workflowChatClient;
    private final ObjectMapper objectMapper;

    /** 注入工具执行器与无工具的结构化客户端。 */
    public PaperTraderActionHandler(
            ProfileToolCapability capability,
            @Qualifier("agentWorkflowChatClient") ChatClient workflowChatClient,
            ObjectMapper objectMapper) {
        this.capability = capability;
        this.workflowChatClient = workflowChatClient;
        this.objectMapper = objectMapper;
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
        String observation = coerceBatch(result);
        return new AgentStepResult(
                stepIndex, action().id(), AgentStepResult.Status.SUCCEEDED,
                observation, null,
                Map.of("toolCalls", 1, "toolName", "paper-trader"));
    }

    /**
     * 观察已能解析出决策则收成标准 JSON；否则用无工具 entity 收一次。
     * 收不成则保留原文，交给 finish 门槛拒绝结束。
     */
    private String coerceBatch(String raw) {
        String canonical = canonicalBatch(raw);
        if (canonical != null) {
            return canonical;
        }
        try {
            TradeDecisionBatch batch = workflowChatClient.prompt()
                    .user(user -> user.text("""
                            把下面的工具对话收成 TradeDecisionBatch。
                            只保留对话里已经出现的决策，不新增标的、价格或数量。
                            没有可执行决策时，按对话中的 hold/flat 写入 decisions。

                            {transcript}
                            """)
                            .param("transcript", tail(raw)))
                    .call()
                    .entity(TradeDecisionBatch.class);
            String converted = canonicalBatch(batch);
            return converted != null ? converted : raw;
        } catch (RuntimeException exception) {
            log.warn("交易观察收成批次失败: {}", exception.getMessage());
            return raw == null ? "" : raw;
        }
    }

    /** 能解析出至少一条决策时返回标准 JSON。 */
    private String canonicalBatch(String raw) {
        TradeDecisionBatch batch = PartialJsonSalvage.salvageDecisions(raw, objectMapper);
        return canonicalBatch(batch);
    }

    /** 序列化非空批次。 */
    private String canonicalBatch(TradeDecisionBatch batch) {
        if (batch == null || batch.decisions().isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(batch);
        } catch (Exception exception) {
            return null;
        }
    }

    /** 保留对话尾部，决策通常写在末尾。 */
    private static String tail(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.length() <= TRANSCRIPT_TAIL
                ? text
                : text.substring(text.length() - TRANSCRIPT_TAIL);
    }
}
