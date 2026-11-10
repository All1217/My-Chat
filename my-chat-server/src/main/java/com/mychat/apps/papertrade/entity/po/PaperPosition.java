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

/** 模拟炒股持仓行。 */
@TableName("paper_position")
@Data
public class PaperPosition {

    @TableId(type = IdType.INPUT)
    private String id;

    @TableField("account_id")
    private String accountId;

    @TableField("symbol")
    private String symbol;

    @TableField("market")
    private String market;

    @TableField("qty")
    private Integer qty;

    @TableField("avg_cost")
    private BigDecimal avgCost;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updatedAt;
}
