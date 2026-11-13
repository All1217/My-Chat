package com.mychat.apps.papertrade.risk.entity.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 模拟盘一个阶段重置前的成绩。 */
@TableName("paper_stage_summary")
@Data
public class PaperStageSummary {

    @TableId(type = IdType.INPUT)
    private String id;

    @TableField("stage_no")
    private Integer stageNo;

    @TableField("started_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime startedAt;

    @TableField("ended_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime endedAt;

    @TableField("start_cash")
    private BigDecimal startCash;

    @TableField("end_equity")
    private BigDecimal endEquity;

    @TableField("trade_count")
    private Integer tradeCount;

    @TableField("win_rate")
    private BigDecimal winRate;

    @TableField("payoff_ratio")
    private BigDecimal payoffRatio;

    @TableField("max_drawdown")
    private BigDecimal maxDrawdown;

    @TableField("avg_hold_days")
    private BigDecimal avgHoldDays;

    @TableField("total_fee")
    private BigDecimal totalFee;

    @TableField("loss_sources")
    private String lossSources;

    @TableField("summary_text")
    private String summaryText;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdAt;
}
