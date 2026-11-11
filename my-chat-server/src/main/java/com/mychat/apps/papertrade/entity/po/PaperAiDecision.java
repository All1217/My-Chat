package com.mychat.apps.papertrade.entity.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** AI 结构化交易决策。 */
@TableName("paper_ai_decision")
@Data
public class PaperAiDecision {

    public static final String ACTION_BUY = "BUY";
    public static final String ACTION_SELL = "SELL";
    public static final String ACTION_HOLD = "HOLD";
    public static final String ACTION_FLAT = "FLAT";

    public static final String EXEC_PENDING = "PENDING";
    public static final String EXEC_EXECUTED = "EXECUTED";
    public static final String EXEC_SKIPPED = "SKIPPED";
    public static final String EXEC_REJECTED = "REJECTED";
    public static final String EXEC_DISMISSED = "DISMISSED";

    @TableId(type = IdType.INPUT)
    private String id;

    @TableField("run_id")
    private String runId;

    @TableField("action")
    private String action;

    @TableField("symbol")
    private String symbol;

    @TableField("name")
    private String name;

    @TableField("qty")
    private Integer qty;

    @TableField("ref_price")
    private BigDecimal refPrice;

    @TableField("take_profit_pct")
    private BigDecimal takeProfitPct;

    @TableField("stop_loss_pct")
    private BigDecimal stopLossPct;

    @TableField("max_hold_days")
    private Integer maxHoldDays;

    @TableField("cooldown_days")
    private Integer cooldownDays;

    @TableField("confidence")
    private BigDecimal confidence;

    @TableField("rationale")
    private String rationale;

    @TableField("risks_json")
    private String risksJson;

    @TableField("needs_human_review")
    private Boolean needsHumanReview;

    @TableField("exec_status")
    private String execStatus;

    @TableField("skip_reason")
    private String skipReason;

    @TableField("order_id")
    private String orderId;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdAt;
}
