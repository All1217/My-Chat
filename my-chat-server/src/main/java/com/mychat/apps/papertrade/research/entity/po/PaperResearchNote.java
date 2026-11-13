package com.mychat.apps.papertrade.research.entity.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 按代码累积的研究卡片。 */
@TableName("paper_research_note")
@Data
public class PaperResearchNote {

    @TableId(value = "symbol", type = IdType.INPUT)
    private String symbol;

    private String name;

    @TableField("quote_summary")
    private String quoteSummary;

    private String story;

    private String risks;

    private Boolean kept;

    @TableField("reject_reason")
    private String rejectReason;

    @TableField("sources_json")
    private String sourcesJson;

    @TableField("run_id")
    private String runId;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
