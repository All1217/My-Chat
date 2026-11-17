package com.mychat.apps.papertrade.trader;

import com.mychat.apps.papertrade.PaperTradeProperties;
import com.mychat.apps.papertrade.risk.entity.po.PaperRiskSettings;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 锁定策略文案随风控数值变化。 */
class PaperLockedStrategyRendererTest {

    @Test
    void lockedTextIncludesMaxPositionsFromRisk() {
        PaperTradeProperties props = new PaperTradeProperties();
        PaperLockedStrategyRenderer renderer = new PaperLockedStrategyRenderer(props);
        PaperRiskSettings risk = new PaperRiskSettings();
        risk.setMaxPositions(1);
        risk.setRiskBudgetPct(0.02);
        risk.setRiskBudgetTightPct(0.01);
        risk.setDrawdownRestrictPct(0.08);
        risk.setMaxSingleAllocation(0.95);
        risk.setLossStreakLimit(3);
        risk.setBuyPauseDays(2);
        risk.setCooldownDaysStock(2);
        risk.setCooldownDaysEtf(1);
        risk.setStockTakeProfitPct(0.04);
        risk.setStockStopLossPct(0.03);
        risk.setStockMaxHoldDays(10);
        risk.setEtfTakeProfitPct(0.03);
        risk.setEtfStopLossPct(0.02);
        risk.setEtfMaxHoldDays(15);
        risk.setQdiiTakeProfitPct(0.045);
        risk.setQdiiStopLossPct(0.03);
        risk.setQdiiMaxHoldDays(10);

        String locked = renderer.render(risk);
        assertThat(locked).contains("最多持有 1 只标的");
        assertThat(locked).contains("平台锁定策略");

        String composed = renderer.compose(locked, "【一、身份与风格】\n自定义");
        assertThat(composed).startsWith("【平台锁定策略");
        assertThat(composed).contains("【一、身份与风格】");
    }
}
