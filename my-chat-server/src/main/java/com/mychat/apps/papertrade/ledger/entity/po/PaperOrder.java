package com.mychat.apps.papertrade.ledger.entity.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 模拟炒股市价订单行。 */
@TableName("paper_order")
@Data
public class PaperOrder {

    public static final String SIDE_BUY = "BUY";
    public static final String SIDE_SELL = "SELL";
    public static final String STATUS_FILLED = "FILLED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String SOURCE_MANUAL = "MANUAL";
    public static final String SOURCE_AI = "AI";
    public static final String SOURCE_EXIT = "EXIT";

    public static final String EXIT_STOP_LOSS = "STOP_LOSS";
    public static final String EXIT_TAKE_PROFIT = "TAKE_PROFIT";
    public static final String EXIT_MAX_HOLD = "MAX_HOLD";
    public static final String EXIT_AI = "AI";
    public static final String EXIT_MANUAL = "MANUAL";

    @TableId(type = IdType.INPUT)
    private String id;

    @TableField("account_id")
    private String accountId;

    @TableField("side")
    private String side;

    @TableField("symbol")
    private String symbol;

    @TableField("market")
    private String market;

    @TableField("qty")
    private Integer qty;

    @TableField("fill_price")
    private BigDecimal fillPrice;

    @TableField("fee")
    private BigDecimal fee;

    @TableField("source")
    private String source;

    @TableField("decision_id")
    private String decisionId;

    @TableField("status")
    private String status;

    @TableField("reject_reason")
    private String rejectReason;

    @TableField("realized_pnl")
    private BigDecimal realizedPnl;

    @TableField("exit_reason")
    private String exitReason;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdAt;
}
