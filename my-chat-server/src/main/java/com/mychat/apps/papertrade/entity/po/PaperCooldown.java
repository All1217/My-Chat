package com.mychat.apps.papertrade.entity.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;

/** 卖出后冷却期。 */
@TableName("paper_cooldown")
@Data
public class PaperCooldown {

    @TableField("account_id")
    private String accountId;

    @TableField("symbol")
    private String symbol;

    @TableField("until_date")
    private LocalDate untilDate;
}
