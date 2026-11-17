package com.mychat.apps.papertrade.research.entity.vo;

import lombok.Data;

import java.math.BigDecimal;

/** 已留下的研究卡片。 */
@Data
public class PaperResearchNoteVO {
    private String symbol;
    private String name;
    private String story;
    private String risks;
    private String sourceTitles;
    private BigDecimal fitScore;
    private BigDecimal evidenceScore;
    private String signalTags;
    private String sourceTier;
    private Integer valueTier;
    private String updatedAt;
}
