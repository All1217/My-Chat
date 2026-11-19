package com.mychat.apps.papertrade.trader;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 新旧决策 JSON 都能解析。 */
class PaperAiDecisionParseTest {

    private final PaperAiTraderService service = new PaperAiTraderService(
            null, null, null, null, null, null, null, null,
            null, // evaluatorService
            null, null, null, null, null, null);

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

    /** 模型常用 shares/price/reason 也能落到 quantity/referencePrice/reasoning。 */
    @Test
    void readsModelShorthandFieldNames() {
        PaperAiTraderService.RawDecision d = service.parseDecisions("""
                {"action":"BUY","symbol":"601899","name":"紫金矿业","shares":500,
                "price":27.0,"stop_loss":25.18,"take_profit":29.5,
                "confidence":0.72,"reason":"回调企稳"}
                """).get(0);
        assertEquals(500, d.quantity);
        assertEquals(27.0, d.referencePrice);
        assertEquals("回调企稳", d.reasoning);
        assertEquals(29.5, d.takeProfit);
        assertEquals(25.18, d.stopLoss);
    }

    /** TradeDecisionBatch 路径：snake_case 止损止盈与参考价不得被静默丢弃。 */
    @Test
    void typedBatchReadsSnakeCaseStopAndPrice() {
        PaperAiTraderService.RawDecision d = service.parseTypedDecisions("""
                {"decisions":[{"action":"buy","symbol":"159338","name":"中证A500ETF国泰",
                "quantity":18600,"capital_ratio":0.9,"reference_price":1.02,
                "take_profit":0.03,"stop_loss":0.02,"max_hold_days":15,
                "confidence":0.72,"reasoning":"低吸","risk_flags":[],"manual_review":false}]}
                """).get(0);
        assertEquals(18600, d.quantity);
        assertEquals(1.02, d.referencePrice);
        assertEquals(0.03, d.takeProfit);
        assertEquals(0.02, d.stopLoss);
        assertEquals(15, d.maxHoldDays);
    }

    /** TradeDecisionBatch 路径：驼峰字段同样可读。 */
    @Test
    void typedBatchReadsCamelCaseStopAndPrice() {
        PaperAiTraderService.RawDecision d = service.parseTypedDecisions("""
                {"decisions":[{"action":"BUY","symbol":"159338","name":"中证A500ETF国泰",
                "quantity":18600,"capitalRatio":0.9,"referencePrice":1.02,
                "takeProfit":0.03,"stopLoss":0.02,"maxHoldDays":15,
                "confidence":0.72,"rationale":"低吸","riskFlags":["波动"],"manualReview":true}]}
                """).get(0);
        assertEquals(1.02, d.referencePrice);
        assertEquals(0.03, d.takeProfit);
        assertEquals(0.02, d.stopLoss);
        assertEquals("低吸", d.reasoning);
        assertEquals(Boolean.TRUE, d.manualReview);
    }
}
