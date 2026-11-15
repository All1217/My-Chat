package com.mychat.apps.papertrade.research.entity.vo;

import lombok.Data;

import java.math.BigDecimal;

/** 管理端研究卡片（含 kept / 原文）。 */
@Data
public class PaperResearchNoteAdminVO {
    private String symbol;
    private String name;
    private String quoteSummary;
    private String story;
    private String risks;
    private Boolean kept;
    private Boolean watchlist;
    private String rejectReason;
    private String sourcesJson;
    private BigDecimal fitScore;
    private BigDecimal evidenceScore;
    private String signalTags;
    private String sourceTier;
    private String runId;
    private String createdAt;
    private String updatedAt;
}
