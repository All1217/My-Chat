package com.mychat.apps.papertrade.risk.entity.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 阶段总结新建/编辑请求。 */
@Data
public class PaperStageSummaryRequest {
    private Integer stageNo;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private BigDecimal startCash;
    private BigDecimal endEquity;
    private Integer tradeCount;
    private BigDecimal winRate;
    private BigDecimal payoffRatio;
    private BigDecimal maxDrawdown;
    private BigDecimal avgHoldDays;
    private BigDecimal totalFee;
    private String lossSources;
    private String summaryText;
}
