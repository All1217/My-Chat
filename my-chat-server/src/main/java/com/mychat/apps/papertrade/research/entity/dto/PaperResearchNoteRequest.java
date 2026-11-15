package com.mychat.apps.papertrade.research.entity.dto;

import lombok.Data;

/** 研究卡片新建/编辑请求。 */
@Data
public class PaperResearchNoteRequest {
    private String symbol;
    private String name;
    private String story;
    private String risks;
    private Boolean kept;
    private String rejectReason;
    private String sourcesJson;
    private String quoteSummary;
}
