package com.mychat.apps.papertrade.research.entity.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 研究卡片新建/编辑请求。 */
@Data
public class PaperResearchNoteRequest {
    private String symbol;
    private String name;
    private String story;
    private String risks;
    private Boolean kept;
    /** 是否自选；人工新建缺省 true。 */
    private Boolean watchlist;
    private String rejectReason;
    private String sourcesJson;
    private String quoteSummary;
    private BigDecimal fitScore;
    private BigDecimal evidenceScore;
    private String signalTags;
    private String sourceTier;
}
