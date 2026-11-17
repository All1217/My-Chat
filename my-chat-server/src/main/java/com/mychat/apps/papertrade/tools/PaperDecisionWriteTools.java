package com.mychat.apps.papertrade.tools;

import com.mychat.apps.papertrade.trader.PaperAiTraderService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/** 交易员决策落库写工具。 */
@Component
public class PaperDecisionWriteTools {

    private final PaperAiTraderService traderService;

    public PaperDecisionWriteTools(@Lazy PaperAiTraderService traderService) {
        this.traderService = traderService;
    }

    @Tool(description = "把交易员决策批次写入 paper_ai_decision。decisionsJson 为 decisions 数组或 TradeDecisionBatch")
    public String recordAiDecisions(
            @ToolParam(description = "交易员轮次 ID") String runId,
            @ToolParam(description = "决策 JSON") String decisionsJson) {
        try {
            return PaperToolJson.write(traderService.recordDecisionsFromTool(runId, decisionsJson));
        } catch (Exception e) {
            return PaperToolJson.fail(e);
        }
    }
}
