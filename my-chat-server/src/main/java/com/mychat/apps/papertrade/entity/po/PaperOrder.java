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

/** 模拟炒股市价订单行。 */
@TableName("paper_order")
@Data
public class PaperOrder {

    public static final String SIDE_BUY = "BUY";
    public static final String SIDE_SELL = "SELL";
    public static final String STATUS_FILLED = "FILLED";
    public static final String STATUS_REJECTED = "REJECTED";

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

    @TableField("status")
    private String status;

    @TableField("reject_reason")
    private String rejectReason;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdAt;
}
