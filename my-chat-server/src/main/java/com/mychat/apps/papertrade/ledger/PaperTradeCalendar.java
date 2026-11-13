package com.mychat.apps.papertrade.ledger;

import java.time.DayOfWeek;
import java.time.LocalDate;

/** 工作日计数。不含交易所节假日，周六日不算。 */
public final class PaperTradeCalendar {

    private PaperTradeCalendar() {
    }

    /** 开仓日之后、截止日之前（含截止日）的工作日数。 */
    public static int weekdaysSince(LocalDate opened, LocalDate today) {
        if (opened == null || today == null || !today.isAfter(opened)) {
            return 0;
        }
        int days = 0;
        for (LocalDate d = opened.plusDays(1); !d.isAfter(today); d = d.plusDays(1)) {
            if (isWeekday(d)) {
                days++;
            }
        }
        return days;
    }

    /** 从 start 再往后数 n 个工作日，返回那一天。n<=0 时返回 start。 */
    public static LocalDate plusWeekdays(LocalDate start, int n) {
        LocalDate d = start == null ? LocalDate.now() : start;
        int left = Math.max(0, n);
        while (left > 0) {
            d = d.plusDays(1);
            if (isWeekday(d)) {
                left--;
            }
        }
        return d;
    }

    private static boolean isWeekday(LocalDate d) {
        DayOfWeek w = d.getDayOfWeek();
        return w != DayOfWeek.SATURDAY && w != DayOfWeek.SUNDAY;
    }
}
