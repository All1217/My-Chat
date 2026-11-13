package com.mychat.apps.market;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 溢价计算：列表现价、已知收盘价、净值降级。 */
class EtfPremiumClientTest {

    @Test
    void usesListPriceAgainstIopv() {
        EtfPremiumClient.PremiumQuote q = EtfPremiumClient.quoteOf(
                "510300", "沪深300ETF华泰柏瑞", 4.118, 4.117, -0.02, 4.117, null);
        assertEquals("IOPV", q.getPremiumBasis());
        assertEquals(4.118, q.getPrice());
        assertEquals(0.0243, q.getPremiumPct(), 0.0001);
    }

    @Test
    void usesKnownPriceWhenListPriceMissing() {
        EtfPremiumClient.PremiumQuote q = EtfPremiumClient.quoteOf(
                "513100", "纳指ETF国泰", 0.0, 1.8923, null, 2.218, null);
        assertEquals("IOPV", q.getPremiumBasis());
        assertEquals(2.218, q.getPrice());
        assertEquals(17.2119, q.getPremiumPct(), 0.0001);
    }

    @Test
    void fallsBackToNavWhenIopvMissingOrSentinel() {
        EtfPremiumClient.PremiumQuote q = EtfPremiumClient.quoteOf(
                "510300", "沪深300ETF华泰柏瑞", null, 530037311.0, null, 4.117, 4.116);
        assertEquals("NAV_DELAYED", q.getPremiumBasis());
        assertEquals(0.0243, q.getPremiumPct(), 0.0001);
    }

    @Test
    void returnsNullWhenNothingToCompare() {
        assertNull(EtfPremiumClient.quoteOf("510300", null, null, null, null, null, null));
    }
}
