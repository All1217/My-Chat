package com.mychat.apps.market;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 代码识别与交易日生成。 */
class MarketSymbolParserTest {

    @Test
    void parsesAShareDigitsAndPrefixes() {
        assertEquals("600519", MarketSymbolParser.parse("600519").symbol());
        assertEquals("CN", MarketSymbolParser.parse("600519").market());
        assertEquals("1.600519", MarketSymbolParser.parse("sh600519").eastMoneySecId());
        assertEquals("0.000001", MarketSymbolParser.parse("000001.SZ").eastMoneySecId());
        assertEquals("0.300750", MarketSymbolParser.parse("300750").eastMoneySecId());
    }

    @Test
    void parsesAShareEtfsAndExchangeColon() {
        assertEquals("0.159659", MarketSymbolParser.parse("159659").eastMoneySecId());
        assertEquals("1.513500", MarketSymbolParser.parse("513500").eastMoneySecId());
        assertEquals("0.159659", MarketSymbolParser.parse("SZ:159659").eastMoneySecId());
        assertEquals("1.513500", MarketSymbolParser.parse("SH:513500").eastMoneySecId());
        assertEquals("1.513500", MarketSymbolParser.parse("513500.SH").eastMoneySecId());
    }

    @Test
    void parsesUsTicker() {
        ResolvedSymbol apple = MarketSymbolParser.parse("aapl");
        assertEquals("AAPL", apple.symbol());
        assertEquals("US", apple.market());
    }

    @Test
    void parsesUsIndexWithNasdaqDotPrefix() {
        ResolvedSymbol ixic = MarketSymbolParser.parse("NASDAQ:.IXIC");
        assertEquals("IXIC", ixic.symbol());
        assertEquals("US", ixic.market());
        ResolvedSymbol ndx = MarketSymbolParser.parse("NASDAQ:.NDX");
        assertEquals("NDX", ndx.symbol());
        assertEquals("US", ndx.market());
    }

    @Test
    void rejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> MarketSymbolParser.parse(" "));
    }

    @Test
    void nextTradingDaysSkipWeekend() {
        List<String> days = MarketCalendar.nextTradingDays(LocalDate.of(2026, 9, 4), 3);
        assertEquals(List.of("2026-09-07", "2026-09-08", "2026-09-09"), days);
    }

    @Test
    void forecastBarsIsOneThirdAtLeastFiveCappedAtSixty() {
        assertEquals(5, MarketCalendar.forecastBars(10));
        assertEquals(20, MarketCalendar.forecastBars(60));
        assertTrue(MarketCalendar.forecastBars(3) >= 5);
        assertEquals(20, MarketCalendar.forecastBars(3000));
    }

    @Test
    void requireRangeAcceptsEightKeys() {
        for (String key : List.of("1M", "3M", "6M", "1Y", "3Y", "5Y", "10Y", "ALL")) {
            assertEquals(key, MarketCalendar.requireRange(key));
        }
        assertThrows(IllegalArgumentException.class, () -> MarketCalendar.requireRange("2Y"));
    }

    @Test
    void cutoffDateIsNullForAllAndThreeYearsBackFor3Y() {
        assertEquals(null, MarketCalendar.cutoffDate("ALL"));
        assertEquals(LocalDate.now().minusYears(3), MarketCalendar.cutoffDate("3Y"));
        assertEquals(LocalDate.now().minusMonths(12), MarketCalendar.cutoffDate("1Y"));
    }

    @Test
    void yahooAndEastMoneyParamsMatchRange() {
        assertEquals("5y", MarketCalendar.yahooRange("3Y"));
        assertEquals("max", MarketCalendar.yahooRange("ALL"));
        assertEquals(1500, MarketCalendar.eastMoneyLimit("ALL"));
        assertEquals(850, MarketCalendar.eastMoneyLimit("3Y"));
        assertEquals("102", MarketCalendar.eastMoneyKlt("10Y"));
        assertEquals("101", MarketCalendar.eastMoneyKlt("1Y"));
    }
}
