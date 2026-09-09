package com.mychat.apps.market;

import com.mychat.apps.market.entity.vo.KlinePointVO;
import com.mychat.apps.market.entity.vo.MarketQuoteVO;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 日 K 抄底指标。 */
class MarketDipIndicatorsTest {

    /** 20 根收在高点下方时回撤为负。 */
    @Test
    void drawdownFromWindowHigh() {
        List<Double> closes = new ArrayList<>();
        List<Double> highs = new ArrayList<>();
        for (int i = 0; i < 19; i++) {
            closes.add(100.0);
            highs.add(100.0);
        }
        closes.add(90.0);
        highs.add(95.0);
        assertEquals(-10.0, MarketDipIndicators.drawdownPct(closes, highs, 20), 0.01);
    }

    /** 均价 10、最新 12 时相对均线为正。 */
    @Test
    void vsMa20UsesLastWindow() {
        List<Double> closes = new ArrayList<>();
        for (int i = 0; i < 19; i++) {
            closes.add(10.0);
        }
        closes.add(12.0);
        double vs = MarketDipIndicators.vsMaPct(closes, 20);
        assertTrue(vs > 18.0 && vs < 20.0);
    }

    /** 连续下跌时 RSI 接近 0。 */
    @Test
    void rsiIsLowOnStraightDecline() {
        List<Double> closes = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            closes.add(100.0 - i);
        }
        assertEquals(0.0, MarketDipIndicators.rsi(closes, 14), 0.01);
    }

    /** 最近放量时量比大于 1。 */
    @Test
    void volumeRatioAboveOneWhenLastBarSpikes() {
        List<Long> volumes = new ArrayList<>();
        for (int i = 0; i < 19; i++) {
            volumes.add(100L);
        }
        volumes.add(200L);
        assertEquals(200.0 / 105.0, MarketDipIndicators.volumeRatio(volumes, 20), 0.01);
    }

    /** 连续收跌天数从最新一根往回数。 */
    @Test
    void downDaysCountsConsecutiveDeclines() {
        assertEquals(3, MarketDipIndicators.downDays(List.of(10.0, 9.0, 8.0, 7.0)));
        assertEquals(0, MarketDipIndicators.downDays(List.of(7.0, 8.0)));
    }

    /** 行情对象能产出带代码的快照。 */
    @Test
    void snapshotCopiesSymbolAndLastPrice() {
        MarketQuoteVO quote = new MarketQuoteVO();
        quote.setSymbol("600519");
        quote.setName("茅台");
        quote.setHistory(bars(10, 100));
        MarketDipIndicators.Snapshot snap = MarketDipIndicators.from(quote);
        assertEquals("600519", snap.symbol());
        assertEquals(100.0, snap.lastPrice());
    }

    private static List<KlinePointVO> bars(int n, double close) {
        List<KlinePointVO> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            KlinePointVO bar = new KlinePointVO();
            bar.setDate("2026-01-" + String.format("%02d", i + 1));
            bar.setOpen(close);
            bar.setClose(close);
            bar.setHigh(close);
            bar.setLow(close);
            bar.setVolume(1000L);
            list.add(bar);
        }
        return list;
    }
}
