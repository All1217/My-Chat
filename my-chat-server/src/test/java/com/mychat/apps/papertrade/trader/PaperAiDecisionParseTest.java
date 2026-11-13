package com.mychat.apps.papertrade.trader;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 新旧决策 JSON 都能解析。 */
class PaperAiDecisionParseTest {

    private final PaperAiTraderService service = new PaperAiTraderService(
            null, null, null, null, null, null, null, null, null, null, null);

    @Test
    void readsNewFieldNames() {
        PaperAiTraderService.RawDecision d = service.parseDecisions("""
                [{"action":"buy","symbol":"600519","quantity":200,"capital_ratio":0.4,
                "reference_price":10.5,"take_profit":0.04,"stop_loss":0.03,"max_hold_days":10,
                "cooldown_until":2,"confidence":0.8,"reasoning":"低点且企稳",
                "risk_flags":["跳空"],"manual_review":true,"asset_type":"A股"}]
                """).get(0);
        assertEquals("buy", d.action);
        assertEquals(200, d.quantity);
        assertEquals(0.4, d.capitalRatio);
        assertEquals(10.5, d.referencePrice);
        assertEquals(0.04, d.takeProfit);
        assertEquals(0.03, d.stopLoss);
        assertEquals(10, d.maxHoldDays);
        assertEquals(2, d.cooldownUntil.intValue());
        assertEquals("低点且企稳", d.reasoning);
        assertEquals(Boolean.TRUE, d.manualReview);
    }

    @Test
    void readsLegacyFieldNames() {
        PaperAiTraderService.RawDecision d = service.parseDecisions("""
                {"action":"SELL","symbol":"510300","qty":100,"allocationPct":0.2,
                "refPrice":4.2,"takeProfitPct":0.03,"stopLossPct":0.02,
                "maxHoldDays":15,"cooldownDays":1,"confidence":0.7,
                "rationale":"止盈","risks":["流动性"],"needsHumanReview":false}
                """).get(0);
        assertEquals(100, d.quantity);
        assertEquals(0.2, d.capitalRatio);
        assertEquals(4.2, d.referencePrice);
        assertEquals(0.03, d.takeProfit);
        assertEquals(1, d.cooldownDays);
        assertEquals("止盈", d.reasoning);
        assertNull(d.cooldownUntil);
        assertEquals(Boolean.FALSE, d.manualReview);
    }
}
