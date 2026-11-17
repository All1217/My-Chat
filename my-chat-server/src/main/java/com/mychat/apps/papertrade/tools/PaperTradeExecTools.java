package com.mychat.apps.papertrade.tools;

import com.mychat.apps.papertrade.trader.PaperAiTraderService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/** 交易员下单执行写工具（内含风控）。 */
@Component
public class PaperTradeExecTools {

    private final PaperAiTraderService traderService;

    public PaperTradeExecTools(@Lazy PaperAiTraderService traderService) {
        this.traderService = traderService;
    }

    @Tool(description = "按已落库的决策 ID 执行下单（走账户风控与整手约束）")
    public String placePaperOrder(
            @ToolParam(description = "paper_ai_decision.id") String decisionId) {
        try {
            return PaperToolJson.write(traderService.placeFromTool(decisionId));
        } catch (Exception e) {
            return PaperToolJson.fail(e);
        }
    }
}
