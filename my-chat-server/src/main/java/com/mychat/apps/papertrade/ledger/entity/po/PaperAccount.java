package com.mychat.apps.papertrade.ledger.entity.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 模拟炒股虚拟账户行。 */
@TableName("paper_account")
@Data
public class PaperAccount {

    /** 默认账户固定 ID，便于单账户 MVP。 */
    public static final String DEFAULT_ID = "default";

    @TableId(type = IdType.INPUT)
    private String id;

    @TableField("name")
    private String name;

    @TableField("cash")
    private BigDecimal cash;

    @TableField("initial_cash")
    private BigDecimal initialCash;

    @TableField("peak_equity")
    private BigDecimal peakEquity;

    @TableField("max_drawdown")
    private BigDecimal maxDrawdown;

    @TableField("loss_streak")
    private Integer lossStreak;

    @TableField("buy_pause_until")
    private LocalDate buyPauseUntil;

    @TableField("stage_started_at")
    private LocalDateTime stageStartedAt;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updatedAt;
}
