package com.mychat.apps.papertrade.trader.entity.dto;

import lombok.Data;

import java.math.BigDecimal;

/** AI 决策管理端编辑（不新建）。 */
@Data
public class PaperAiDecisionUpdateRequest {
    private String action;
    private String symbol;
    private String name;
    private Integer qty;
    private BigDecimal refPrice;
    private BigDecimal confidence;
    private String rationale;
    private String execStatus;
    private String skipReason;
    private Boolean needsHumanReview;
}
