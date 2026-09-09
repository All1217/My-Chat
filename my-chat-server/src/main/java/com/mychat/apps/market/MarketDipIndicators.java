package com.mychat.apps.market;

import com.mychat.apps.market.entity.vo.KlinePointVO;
import com.mychat.apps.market.entity.vo.MarketQuoteVO;

import java.util.ArrayList;
import java.util.List;

/** 用日 K 算出抄底快照：回撤、均线、RSI、量比、连跌。 */
public final class MarketDipIndicators {

    private MarketDipIndicators() {
    }

    /** 一只自选股的指标行，缺数据的字段为 null。 */
    public record Snapshot(
            String symbol,
            String name,
            Double lastPrice,
            Double changePct,
            Double drawdown20Pct,
            Double drawdown60Pct,
            Double vsMa20Pct,
            Double vsMa60Pct,
            Double rsi14,
            Double volumeRatio,
            Integer downDays
    ) {
    }

    /** 从一年日 K 生成一行快照。 */
    public static Snapshot from(MarketQuoteVO quote) {
        if (quote == null || quote.getHistory() == null || quote.getHistory().isEmpty()) {
            return new Snapshot(
                    quote == null ? "" : quote.getSymbol(),
                    quote == null ? "" : quote.getName(),
                    null, null, null, null, null, null, null, null, null);
        }
        List<KlinePointVO> bars = quote.getHistory();
        List<Double> closes = mapCloses(bars);
        List<Double> highs = mapHighs(bars);
        List<Long> volumes = mapVolumes(bars);
        Double last = last(closes);
        return new Snapshot(
                nullToEmpty(quote.getSymbol()),
                nullToEmpty(quote.getName()),
                last,
                quote.getChangePct() != null ? round2(quote.getChangePct()) : changePct(closes),
                round2(drawdownPct(closes, highs, 20)),
                round2(drawdownPct(closes, highs, 60)),
                round2(vsMaPct(closes, 20)),
                round2(vsMaPct(closes, 60)),
                round2(rsi(closes, 14)),
                round2(volumeRatio(volumes, 20)),
                downDays(closes)
        );
    }

    /** 相对近 N 日最高价的回撤（负值表示低于高点）。 */
    static Double drawdownPct(List<Double> closes, List<Double> highs, int window) {
        if (closes.size() < window || highs.size() < window) {
            return null;
        }
        Double close = last(closes);
        if (close == null || close == 0) {
            return null;
        }
        double peak = Double.NEGATIVE_INFINITY;
        int from = highs.size() - window;
        for (int i = from; i < highs.size(); i++) {
            Double high = highs.get(i);
            if (high != null) {
                peak = Math.max(peak, high);
            }
        }
        if (!Double.isFinite(peak) || peak == 0) {
            return null;
        }
        return (close - peak) / peak * 100.0;
    }

    /** 收盘相对 N 日均线的百分差。 */
    static Double vsMaPct(List<Double> closes, int window) {
        Double ma = sma(closes, window);
        Double close = last(closes);
        if (ma == null || close == null || ma == 0) {
            return null;
        }
        return (close - ma) / ma * 100.0;
    }

    /** Wilder RSI。 */
    static Double rsi(List<Double> closes, int period) {
        if (closes.size() < period + 1) {
            return null;
        }
        double avgGain = 0;
        double avgLoss = 0;
        for (int i = 1; i <= period; i++) {
            double change = closes.get(i) - closes.get(i - 1);
            if (change >= 0) {
                avgGain += change;
            } else {
                avgLoss -= change;
            }
        }
        avgGain /= period;
        avgLoss /= period;
        for (int i = period + 1; i < closes.size(); i++) {
            double change = closes.get(i) - closes.get(i - 1);
            double gain = change > 0 ? change : 0;
            double loss = change < 0 ? -change : 0;
            avgGain = (avgGain * (period - 1) + gain) / period;
            avgLoss = (avgLoss * (period - 1) + loss) / period;
        }
        if (avgLoss == 0) {
            return 100.0;
        }
        double rs = avgGain / avgLoss;
        return 100.0 - 100.0 / (1.0 + rs);
    }

    /** 当日量 / 近 N 日均量。 */
    static Double volumeRatio(List<Long> volumes, int window) {
        if (volumes.size() < window) {
            return null;
        }
        long sum = 0;
        int from = volumes.size() - window;
        for (int i = from; i < volumes.size(); i++) {
            Long v = volumes.get(i);
            if (v == null) {
                return null;
            }
            sum += v;
        }
        double avg = sum / (double) window;
        Long last = volumes.get(volumes.size() - 1);
        if (avg == 0 || last == null) {
            return null;
        }
        return last / avg;
    }

    /** 从最新一根往回数连续收跌的天数。 */
    static Integer downDays(List<Double> closes) {
        if (closes.size() < 2) {
            return 0;
        }
        int days = 0;
        for (int i = closes.size() - 1; i > 0; i--) {
            Double cur = closes.get(i);
            Double prev = closes.get(i - 1);
            if (cur == null || prev == null || cur >= prev) {
                break;
            }
            days++;
        }
        return days;
    }

    private static Double sma(List<Double> values, int window) {
        if (values.size() < window) {
            return null;
        }
        double sum = 0;
        int from = values.size() - window;
        for (int i = from; i < values.size(); i++) {
            Double v = values.get(i);
            if (v == null) {
                return null;
            }
            sum += v;
        }
        return sum / window;
    }

    private static Double changePct(List<Double> closes) {
        if (closes.size() < 2) {
            return null;
        }
        Double last = last(closes);
        Double prev = closes.get(closes.size() - 2);
        if (last == null || prev == null || prev == 0) {
            return null;
        }
        return (last - prev) / prev * 100.0;
    }

    private static List<Double> mapCloses(List<KlinePointVO> bars) {
        List<Double> out = new ArrayList<>(bars.size());
        for (KlinePointVO bar : bars) {
            out.add(bar.getClose());
        }
        return out;
    }

    private static List<Double> mapHighs(List<KlinePointVO> bars) {
        List<Double> out = new ArrayList<>(bars.size());
        for (KlinePointVO bar : bars) {
            if (bar.getHigh() != null) {
                out.add(bar.getHigh());
            } else {
                out.add(bar.getClose());
            }
        }
        return out;
    }

    private static List<Long> mapVolumes(List<KlinePointVO> bars) {
        List<Long> out = new ArrayList<>(bars.size());
        for (KlinePointVO bar : bars) {
            out.add(bar.getVolume());
        }
        return out;
    }

    private static Double last(List<Double> values) {
        return values.isEmpty() ? null : values.get(values.size() - 1);
    }

    private static Double round2(Double value) {
        if (value == null || !Double.isFinite(value)) {
            return null;
        }
        return Math.round(value * 100.0) / 100.0;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
