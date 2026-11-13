package com.mychat.apps.papertrade.research.entity.vo;

import lombok.Data;

/** 已留下的研究卡片。 */
@Data
public class PaperResearchNoteVO {
    private String symbol;
    private String name;
    private String story;
    private String risks;
    private String sourceTitles;
    private String updatedAt;
}
