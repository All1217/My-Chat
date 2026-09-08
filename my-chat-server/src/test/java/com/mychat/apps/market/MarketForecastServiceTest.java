package com.mychat.apps.market;

import com.mychat.apps.market.entity.vo.KlinePointVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 预测点对齐与历史抽样。 */
class MarketForecastServiceTest {

    @Test
    void alignPointsUsesIndexThenLastClose() {
        MarketForecastService.ForecastOutput raw = new MarketForecastService.ForecastOutput(
                "偏强震荡",
                List.of(new MarketForecastService.ForecastPoint("2026-01-01", 10.0)));
        List<KlinePointVO> out = MarketForecastService.alignPoints(
                List.of("2026-01-01", "2026-01-02"), raw);
        assertEquals(10.0, out.get(0).getClose());
        assertEquals(10.0, out.get(1).getClose());
        assertEquals("2026-01-02", out.get(1).getDate());
    }

    @Test
    void formatSampleKeepsLastBar() {
        List<KlinePointVO> history = List.of(
                bar("2026-01-01", 1),
                bar("2026-01-02", 2),
                bar("2026-01-03", 3));
        String sample = MarketForecastService.formatSample(history);
        assertTrue(sample.contains("2026-01-01,1.0"));
        assertTrue(sample.contains("2026-01-03,3.0"));
    }

    private static KlinePointVO bar(String date, double close) {
        KlinePointVO p = new KlinePointVO();
        p.setDate(date);
        p.setClose(close);
        return p;
    }
}
