package com.mychat.apps.papertrade;

import com.mychat.apps.market.entity.vo.KlinePointVO;
import com.mychat.apps.papertrade.ledger.PaperTradeCalendar;
import com.mychat.apps.papertrade.risk.PaperPositionExitService;
import com.mychat.apps.papertrade.risk.PaperSymbolFacts;
import com.mychat.apps.papertrade.risk.PaperSymbolGate;
import com.mychat.apps.market.entity.vo.MarketQuoteVO;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 工作日、涨跌停和出场优先级。 */
class PaperTradeRuleTest {

    @Test
    void weekdaysSkipWeekend() {
        LocalDate friday = LocalDate.of(2026, 11, 13);
        assertEquals(DayOfWeek.FRIDAY, friday.getDayOfWeek());
        assertEquals(LocalDate.of(2026, 11, 17), PaperTradeCalendar.plusWeekdays(friday, 2));
        assertEquals(4, PaperTradeCalendar.weekdaysSince(LocalDate.of(2026, 11, 9), friday));
    }

    @Test
    void stopLossBeatsTakeProfit() {
        assertEquals("STOP_LOSS", PaperPositionExitService.pickExit(
                new java.math.BigDecimal("-0.04"),
                new java.math.BigDecimal("0.04"),
                new java.math.BigDecimal("0.03"),
                1, 10));
        assertEquals("TAKE_PROFIT", PaperPositionExitService.pickExit(
                new java.math.BigDecimal("0.05"),
                new java.math.BigDecimal("0.04"),
                new java.math.BigDecimal("0.03"),
                1, 10));
        assertEquals("MAX_HOLD", PaperPositionExitService.pickExit(
                new java.math.BigDecimal("0.01"),
                new java.math.BigDecimal("0.04"),
                new java.math.BigDecimal("0.03"),
                10, 10));
    }

    @Test
    void limitUpAndAmount() {
        MarketQuoteVO quote = new MarketQuoteVO();
        quote.setSymbol("600519");
        quote.setName("*ST测试");
        quote.setChangePct(4.9);
        List<KlinePointVO> bars = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            KlinePointVO bar = new KlinePointVO();
            bar.setDate("2026-01-" + (i + 1));
            bar.setOpen(10.0);
            bar.setClose(10.2);
            bar.setHigh(10.3);
            bar.setLow(9.9);
            bar.setVolume(1000L);
            bar.setAmount(1_000_000d);
            bars.add(bar);
        }
        quote.setHistory(bars);
        PaperSymbolFacts facts = new PaperSymbolGate().analyze(quote);
        assertTrue(facts.st());
        assertTrue(facts.limitUp());
        assertFalse(facts.limitDown());
        assertEquals(1_000_000d, facts.avgAmount20());
        assertTrue(facts.stabilized());
    }
}
