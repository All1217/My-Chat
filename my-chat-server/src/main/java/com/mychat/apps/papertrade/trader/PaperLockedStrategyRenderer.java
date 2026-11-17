package com.mychat.apps.papertrade.trader;

import com.mychat.apps.papertrade.PaperTradeProperties;
import com.mychat.apps.papertrade.risk.entity.po.PaperRiskSettings;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Locale;

/** 按风控配置 + YAML 过滤门槛渲染不可手改的锁定策略文案。 */
@Component
public class PaperLockedStrategyRenderer {

    private final PaperTradeProperties properties;

    public PaperLockedStrategyRenderer(PaperTradeProperties properties) {
        this.properties = properties;
    }

    /** 生成锁定区（三–七 + 平台不可改说明）。 */
    public String render(PaperRiskSettings risk) {
        int maxPos = n(risk.getMaxPositions(), 2);
        double alloc = pct(risk.getMaxSingleAllocation(), 0.95);
        double budget = pct(risk.getRiskBudgetPct(), 0.02);
        double tight = pct(risk.getRiskBudgetTightPct(), 0.01);
        double dd = pct(risk.getDrawdownRestrictPct(), 0.08);
        int streak = n(risk.getLossStreakLimit(), 3);
        int pause = n(risk.getBuyPauseDays(), 2);
        int cdStock = n(risk.getCooldownDaysStock(), 2);
        int cdEtf = n(risk.getCooldownDaysEtf(), 1);
        double sTp = pct(risk.getStockTakeProfitPct(), 0.04);
        double sSl = pct(risk.getStockStopLossPct(), 0.03);
        int sHold = n(risk.getStockMaxHoldDays(), 10);
        double eTp = pct(risk.getEtfTakeProfitPct(), 0.03);
        double eSl = pct(risk.getEtfStopLossPct(), 0.02);
        int eHold = n(risk.getEtfMaxHoldDays(), 15);
        double qTp = pct(risk.getQdiiTakeProfitPct(), 0.045);
        double qSl = pct(risk.getQdiiStopLossPct(), 0.03);
        int qHold = n(risk.getQdiiMaxHoldDays(), 10);

        BigDecimal cash = properties.getInitialCash() == null
                ? new BigDecimal("20000") : properties.getInitialCash();
        BigDecimal minFee = properties.getMinCommission() == null
                ? new BigDecimal("5") : properties.getMinCommission();
        double commissionCap = properties.getCommissionToProfitMax() <= 0
                ? 0.20 : properties.getCommissionToProfitMax();
        double stockLiq = properties.getMinAvgAmountStock() / 10_000d;
        double etfLiq = properties.getMinAvgAmountEtf() / 10_000d;
        int stockBars = properties.getMinListedBarsStock();
        int etfBars = properties.getMinListedBarsEtf();
        double etfPrem = properties.getMaxEtfPremiumPct();
        double qdiiPrem = properties.getMaxQdiiPremiumPct();

        return """
                【平台锁定策略 · 与闸门同步 · 不可手改】
                初始模拟资金参考 %s 元。下列数值来自运行时风控配置与平台 YAML 过滤门槛。

                【三、仓位与资金约束】
                单标的计划资金上限为可用资金的 %s，但必须叠加以下硬约束，任一不满足则跳过该标的或空仓：
                （1）整手约束：A股和ETF买入数量必须为100股（份）的整数倍。不足1手自动跳过。
                （2）最低佣金约束：单笔须覆盖最低佣金（默认每笔 %s 元）。佣金占预期利润超过 %s 则降低仓位或跳过。
                （3）单笔风险预算：最大可接受亏损不超过账户当前权益的 %s。整手突破则减少股数或跳过。
                （4）持仓集中度：同一时间最多持有 %d 只标的；同类型ETF最多一只。
                （5）禁止同一笔交易内加仓：持仓期间不得追加买入。冷却期过后方可视为全新交易。

                【四、止损、止盈与时间退出（建议默认，写入决策后由持仓扫描执行）】
                （1）普通A股：止盈约 %s，止损约 %s，最长持有约 %d 个交易日。
                （2）境内低波ETF：止盈约 %s，止损约 %s，最长持有约 %d 个交易日。
                （3）跨境QDII ETF：止盈约 %s，止损约 %s，最长持有约 %d 个交易日。
                （4）退出优先级：止损优先于止盈；时间止损在止盈未触发时强制离场。
                （5）卖出后冷却：普通A股冷却 %d 个交易日，ETF冷却 %d 个交易日。

                【五、标的过滤（买入前必须通过 · YAML 门槛）】
                普通A股：排除ST、停牌、上市不足 %d 根K线、20日均成交额低于 %s 万元的标的。
                境内ETF：排除停牌、20日均成交额低于 %s 万元、上市不足 %d 根K线的标的。
                跨境QDII ETF：溢价率不可得则不买；普通ETF溢价上限约 %s%%，QDII约 %s%%。

                【六、账户级风控与重置规则】
                （1）连续亏损冷却：连续 %d 笔以止损离场后，暂停新开仓 %d 个交易日。
                （2）账户回撤熔断：权益从阶段高点回撤达到 %s，将单笔风险预算从 %s 降至 %s，并只允许交易流动性最好的ETF，直到回撤修复。
                （3）流动性枯竭重置：现金买不起任何合法一手且无可卖持仓时触发重置；重置前输出阶段总结。
                （4）重置后从初始资金重新开始，保留阶段总结作参考。

                【七、输出与执行（平台硬约束，不可改）】
                决策须符合 TradeDecisionBatch / 工具落库约定；买入仅 valueTier=1；valueTier=0 只可卖；
                只能交易 candidates 与 positions 中的代码；hold/flat 不下单；buy/sell 须给 stop_loss 与 take_profit。
                value_tier 语义：0已持仓 1应该买入 2继续观察 3移出票仓（成交与清理任务回写，禁止模型直接写0）。
                """.formatted(
                cash.toPlainString(),
                pctLabel(alloc),
                minFee.toPlainString(),
                pctLabel(commissionCap),
                pctLabel(budget),
                maxPos,
                pctLabel(sTp), pctLabel(sSl), sHold,
                pctLabel(eTp), pctLabel(eSl), eHold,
                pctLabel(qTp), pctLabel(qSl), qHold,
                cdStock, cdEtf,
                stockBars, fmtWan(stockLiq),
                fmtWan(etfLiq), etfBars,
                fmtNum(etfPrem), fmtNum(qdiiPrem),
                streak, pause,
                pctLabel(dd), pctLabel(budget), pctLabel(tight)
        ).trim();
    }

    /** 拼接锁定区与用户风格区。 */
    public String compose(String locked, String custom) {
        String a = locked == null ? "" : locked.trim();
        String b = custom == null ? "" : custom.trim();
        if (a.isEmpty()) {
            return b;
        }
        if (b.isEmpty()) {
            return a;
        }
        return a + "\n\n" + b;
    }

    private static String pctLabel(double v) {
        return String.format(Locale.ROOT, "%.2f%%", v * 100.0);
    }

    private static String fmtNum(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    private static String fmtWan(double wan) {
        if (wan >= 100 && Math.abs(wan - Math.rint(wan)) < 0.01) {
            return String.format(Locale.ROOT, "%.0f", wan);
        }
        return String.format(Locale.ROOT, "%.1f", wan);
    }

    private static int n(Integer v, int fallback) {
        return v == null || v <= 0 ? fallback : v;
    }

    private static double pct(Double v, double fallback) {
        return v == null || v <= 0 ? fallback : v;
    }
}
