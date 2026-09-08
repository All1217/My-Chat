package com.mychat.apps.market;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** 交易日辅助：v1 只跳过周末。 */
public final class MarketCalendar {

    private static final Set<String> RANGE_KEYS =
            Set.of("1M", "3M", "6M", "1Y", "3Y", "5Y", "10Y", "ALL");
    private static final int FORECAST_BAR_CAP = 20;

    private MarketCalendar() {
    }

    /** 校验时间段键。 */
    public static String requireRange(String rangeKey) {
        if (rangeKey == null) {
            throw new IllegalArgumentException("请选择时间段");
        }
        String key = rangeKey.trim().toUpperCase();
        if (!RANGE_KEYS.contains(key)) {
            throw new IllegalArgumentException("时间段仅支持 1M / 3M / 6M / 1Y / 3Y / 5Y / 10Y / ALL");
        }
        return key;
    }

    /**
     * 有限档的历史起点；上市以来返回 null，表示不截断。
     */
    public static LocalDate cutoffDate(String rangeKey) {
        String key = requireRange(rangeKey);
        if ("ALL".equals(key)) {
            return null;
        }
        LocalDate now = LocalDate.now();
        return switch (key) {
            case "1M" -> now.minusMonths(1);
            case "3M" -> now.minusMonths(3);
            case "6M" -> now.minusMonths(6);
            case "1Y" -> now.minusMonths(12);
            case "3Y" -> now.minusYears(3);
            case "5Y" -> now.minusYears(5);
            case "10Y" -> now.minusYears(10);
            default -> throw new IllegalArgumentException("时间段仅支持 1M / 3M / 6M / 1Y / 3Y / 5Y / 10Y / ALL");
        };
    }

    /** Yahoo chart 的 range 参数。3Y 无对应档，先拉 5y 再截。 */
    public static String yahooRange(String rangeKey) {
        return switch (requireRange(rangeKey)) {
            case "1M" -> "1mo";
            case "3M" -> "3mo";
            case "6M" -> "6mo";
            case "1Y" -> "1y";
            case "3Y", "5Y" -> "5y";
            case "10Y" -> "10y";
            default -> "max";
        };
    }

    /** 东方财富 K 线周期：长周期用周线，避免一次拉数万根日 K 超时。 */
    public static String eastMoneyKlt(String rangeKey) {
        return switch (requireRange(rangeKey)) {
            case "5Y", "10Y", "ALL" -> "102";
            default -> "101";
        };
    }

    /** Yahoo interval：长周期用周线。 */
    public static String yahooInterval(String rangeKey) {
        return switch (requireRange(rangeKey)) {
            case "5Y", "10Y", "ALL" -> "1wk";
            default -> "1d";
        };
    }

    /** 东方财富一次拉取的根数，略多于时间段以免截断。 */
    public static int eastMoneyLimit(String rangeKey) {
        return switch (requireRange(rangeKey)) {
            case "1M" -> 40;
            case "3M" -> 90;
            case "6M" -> 160;
            case "1Y" -> 280;
            case "3Y" -> 850;
            case "5Y" -> 280;
            case "10Y" -> 550;
            default -> 1500;
        };
    }

    /** 预测根数：历史的 1/3，至少 5 根，最多 20 根。 */
    public static int forecastBars(int historySize) {
        return Math.min(FORECAST_BAR_CAP, Math.max(5, historySize / 3));
    }

    /**
     * 从最后一根历史日之后，生成 count 个未来交易日（跳过周六日）。
     */
    public static List<String> nextTradingDays(LocalDate lastHistory, int count) {
        List<String> dates = new ArrayList<>();
        LocalDate cursor = lastHistory;
        while (dates.size() < count) {
            cursor = cursor.plusDays(1);
            DayOfWeek dow = cursor.getDayOfWeek();
            if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
                continue;
            }
            dates.add(cursor.toString());
        }
        return dates;
    }
}
