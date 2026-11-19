package com.mychat.apps.papertrade.agent;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.math.BigDecimal;
import java.util.List;

/** 交易员类型化建议输出契约；不包含订单执行能力。 */
public record TradeDecisionBatch(List<TradeDecisionProposal> decisions) {

    /** 固化建议集合。 */
    public TradeDecisionBatch {
        decisions = decisions == null ? List.of() : List.copyOf(decisions);
    }

    /** 单条交易建议契约；字段名同时接受 snake_case 与历史驼峰别名。 */
    public record TradeDecisionProposal(
            String action,
            String symbol,
            String name,
            @JsonAlias({"qty", "shares"})
            Integer quantity,
            @JsonAlias({"allocationPct", "capital_ratio"})
            BigDecimal capitalRatio,
            @JsonAlias({"refPrice", "reference_price", "price"})
            BigDecimal referencePrice,
            @JsonAlias({"takeProfitPct", "take_profit"})
            BigDecimal takeProfit,
            @JsonAlias({"stopLossPct", "stop_loss"})
            BigDecimal stopLoss,
            @JsonAlias("max_hold_days")
            Integer maxHoldDays,
            @JsonAlias({"cooldown_days", "cooldownDays"})
            Integer cooldownDays,
            double confidence,
            @JsonAlias({"rationale", "reason"})
            String reasoning,
            @JsonAlias({"risks", "risk_flags"})
            List<String> riskFlags,
            @JsonAlias({"needsHumanReview", "manual_review"})
            boolean manualReview,
            @JsonDeserialize(using = FlexibleStringListDeserializer.class)
            List<String> evidenceRefs) {

        /** 固化列表字段。 */
        public TradeDecisionProposal {
            riskFlags = riskFlags == null ? List.of() : List.copyOf(riskFlags);
            evidenceRefs = evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs);
        }
    }
}
