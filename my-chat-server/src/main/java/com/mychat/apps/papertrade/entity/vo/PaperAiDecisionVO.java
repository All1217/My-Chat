package com.mychat.apps.papertrade.entity.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** AI 决策一行。 */
@Data
public class PaperAiDecisionVO {
    private String id;
    private String runId;
    private String action;
    private String symbol;
    private String name;
    private Integer qty;
    private BigDecimal refPrice;
    private BigDecimal takeProfitPct;
    private BigDecimal stopLossPct;
    private Integer maxHoldDays;
    private Integer cooldownDays;
    private BigDecimal confidence;
    private String rationale;
    private List<String> risks;
    private Boolean needsHumanReview;
    private String execStatus;
    private String skipReason;
    private String orderId;
    private String createdAt;
}
