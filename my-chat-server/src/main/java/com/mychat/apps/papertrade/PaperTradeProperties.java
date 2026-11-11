package com.mychat.apps.papertrade;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** 模拟炒股配置：资金、费用、风控与 AI 闸门。 */
@Data
@Component
@ConfigurationProperties(prefix = "app.paper-trade")
public class PaperTradeProperties {

    /** 新账户初始现金。 */
    private BigDecimal initialCash = new BigDecimal("1000000");

    /** 佣金费率（买卖均扣）。 */
    private BigDecimal commissionRate = new BigDecimal("0.0003");

    /** A 股卖出印花税率。 */
    private BigDecimal stampDutyRate = new BigDecimal("0.0005");

    /** AI 决策后默认自动下单。 */
    private boolean autoExecute = true;

    /** 置信度低于此值则挂起待确认。 */
    private double reviewConfidenceThreshold = 0.55;

    /** 同时最多持仓标的数。 */
    private int maxPositions = 2;

    /** 单票最大仓位占比（相对现金+持仓市值近似用现金口径时由闸门校验）。 */
    private double maxSingleAllocation = 0.95;

    /** 是否启用盘后定时 AI 轮。 */
    private boolean scheduleEnabled = false;

    /** 盘后 cron（默认工作日 15:35）。 */
    private String scheduleCron = "0 35 15 * * MON-FRI";

    /** ETF/QDII 买入必须拿到溢价。 */
    private boolean requireEtfPremium = true;

    /** 普通场内 ETF 最大溢价率（%）。 */
    private double maxEtfPremiumPct = 2.0;

    /** QDII/跨境 ETF 最大溢价率（%）。 */
    private double maxQdiiPremiumPct = 1.5;

    /** 空自选时的兜底候选代码（逗号分隔）。 */
    private String fallbackSymbols = "513100,159941,510300";
}
