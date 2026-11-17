package com.mychat.apps.papertrade.agent;

import java.math.BigDecimal;
import java.util.List;

/** 交易员类型化建议输出契约；不包含订单执行能力。 */
public record TradeDecisionBatch(List<TradeDecisionProposal> decisions) {

    /** 固化建议集合。 */
    public TradeDecisionBatch {
        decisions = decisions == null ? List.of() : List.copyOf(decisions);
    }

    /** 单条交易建议契约。 */
    public record TradeDecisionProposal(
            String action,
            String symbol,
            String name,
            Integer quantity,
            BigDecimal capitalRatio,
            BigDecimal referencePrice,
            BigDecimal takeProfit,
            BigDecimal stopLoss,
            Integer maxHoldDays,
            Integer cooldownDays,
            double confidence,
            String reasoning,
            List<String> riskFlags,
            boolean manualReview,
            List<String> evidenceRefs) {

        /** 固化列表字段。 */
        public TradeDecisionProposal {
            riskFlags = riskFlags == null ? List.of() : List.copyOf(riskFlags);
            evidenceRefs = evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs);
        }
    }
}
