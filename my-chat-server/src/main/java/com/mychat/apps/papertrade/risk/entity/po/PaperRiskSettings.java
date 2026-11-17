package com.mychat.apps.papertrade.risk.entity.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/** 模拟炒股运行时风控（单行）。 */
@TableName("paper_risk_settings")
@Data
public class PaperRiskSettings {

    public static final String DEFAULT_ID = "default";

    @TableId(type = IdType.INPUT)
    private String id;

    @TableField("max_positions")
    private Integer maxPositions;

    @TableField("max_single_allocation")
    private Double maxSingleAllocation;

    @TableField("risk_budget_pct")
    private Double riskBudgetPct;

    @TableField("risk_budget_tight_pct")
    private Double riskBudgetTightPct;

    @TableField("drawdown_restrict_pct")
    private Double drawdownRestrictPct;

    @TableField("loss_streak_limit")
    private Integer lossStreakLimit;

    @TableField("buy_pause_days")
    private Integer buyPauseDays;

    @TableField("review_confidence_threshold")
    private Double reviewConfidenceThreshold;

    @TableField("auto_execute")
    private Boolean autoExecute;

    @TableField("cooldown_days_stock")
    private Integer cooldownDaysStock;

    @TableField("cooldown_days_etf")
    private Integer cooldownDaysEtf;

    @TableField("stock_take_profit_pct")
    private Double stockTakeProfitPct;

    @TableField("stock_stop_loss_pct")
    private Double stockStopLossPct;

    @TableField("stock_max_hold_days")
    private Integer stockMaxHoldDays;

    @TableField("etf_take_profit_pct")
    private Double etfTakeProfitPct;

    @TableField("etf_stop_loss_pct")
    private Double etfStopLossPct;

    @TableField("etf_max_hold_days")
    private Integer etfMaxHoldDays;

    @TableField("qdii_take_profit_pct")
    private Double qdiiTakeProfitPct;

    @TableField("qdii_stop_loss_pct")
    private Double qdiiStopLossPct;

    @TableField("qdii_max_hold_days")
    private Integer qdiiMaxHoldDays;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updatedAt;
}
