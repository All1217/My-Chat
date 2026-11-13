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
    private BigDecimal initialCash = new BigDecimal("20000");

    /** 佣金费率（买卖均扣）。 */
    private BigDecimal commissionRate = new BigDecimal("0.0003");

    /** 单笔最低佣金（元），买卖都适用。 */
    private BigDecimal minCommission = new BigDecimal("5");

    /** A 股卖出印花税率。 */
    private BigDecimal stampDutyRate = new BigDecimal("0.0005");

    /** 单笔最大亏损占权益比例。 */
    private double riskBudgetPct = 0.02;

    /** 回撤收紧后的单笔风险预算。 */
    private double riskBudgetTightPct = 0.01;

    /** 权益从阶段高点回撤达到该比例后收紧预算。 */
    private double drawdownRestrictPct = 0.08;

    /** 连续止损多少笔后暂停开仓。 */
    private int lossStreakLimit = 3;

    /** 连亏后暂停的工作日数。 */
    private int buyPauseDays = 2;

    /** 普通 A 股 20 日均成交额下限（元）。 */
    private double minAvgAmountStock = 50_000_000d;

    /** ETF 20 日均成交额下限（元）。 */
    private double minAvgAmountEtf = 30_000_000d;

    /** 普通 A 股最少上市 K 线根数。 */
    private int minListedBarsStock = 60;

    /** ETF 最少上市 K 线根数。 */
    private int minListedBarsEtf = 20;

    /** 最低佣金占预期利润的上限。 */
    private double commissionToProfitMax = 0.20;

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

    /** 是否启用搜集定时。与操盘定时分开。 */
    private boolean researchScheduleEnabled = false;

    /** 搜集 cron（默认工作日 15:10，早于操盘）。 */
    private String researchScheduleCron = "0 10 15 * * MON-FRI";

    /** 刚整理过的代码多少天内不再搜集。 */
    private int researchSkipDays = 3;

    /** 每一轮最多送去拉行情的代码数。 */
    private int researchBatchSize = 15;

    /** 每一轮最多新留下的卡片数。 */
    private int researchKeepLimit = 8;

    /** 操盘上下文最多带入的已留卡片数。 */
    private int researchContextLimit = 15;

    /** ETF/QDII 买入必须拿到溢价。 */
    private boolean requireEtfPremium = true;

    /** 普通场内 ETF 最大溢价率（%）。 */
    private double maxEtfPremiumPct = 2.0;

    /** QDII/跨境 ETF 最大溢价率（%）。 */
    private double maxQdiiPremiumPct = 1.5;

    /** 空自选时的兜底候选代码（逗号分隔）。 */
    private String fallbackSymbols = "513100,159941,510300";
}
