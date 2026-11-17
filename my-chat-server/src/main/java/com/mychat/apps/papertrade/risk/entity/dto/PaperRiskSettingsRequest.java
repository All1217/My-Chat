package com.mychat.apps.papertrade.risk.entity.dto;

import lombok.Data;

/** 更新运行时风控请求（字段均可选）。 */
@Data
public class PaperRiskSettingsRequest {
    private Integer maxPositions;
    private Double maxSingleAllocation;
    private Double riskBudgetPct;
    private Double riskBudgetTightPct;
    private Double drawdownRestrictPct;
    private Integer lossStreakLimit;
    private Integer buyPauseDays;
    private Double reviewConfidenceThreshold;
    private Boolean autoExecute;
    private Integer cooldownDaysStock;
    private Integer cooldownDaysEtf;
    private Double stockTakeProfitPct;
    private Double stockStopLossPct;
    private Integer stockMaxHoldDays;
    private Double etfTakeProfitPct;
    private Double etfStopLossPct;
    private Integer etfMaxHoldDays;
    private Double qdiiTakeProfitPct;
    private Double qdiiStopLossPct;
    private Integer qdiiMaxHoldDays;
}
