package com.mychat.apps.papertrade.research;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 搜集名单的纯筛选：买得起、不像涨停、最近没整理过。 */
public final class PaperResearchFilters {

    private PaperResearchFilters() {
    }

    /** 一手（100 股）不超过现金的 95%。 */
    public static boolean affordable(Double price, BigDecimal cash) {
        if (price == null || price <= 0 || cash == null || cash.signum() <= 0) {
            return false;
        }
        BigDecimal lot = BigDecimal.valueOf(price).multiply(BigDecimal.valueOf(100));
        return lot.compareTo(cash.multiply(new BigDecimal("0.95"))) <= 0;
    }

    /** 名称里带 ST。 */
    public static boolean looksSt(String name) {
        if (name == null) {
            return false;
        }
        String n = name.toUpperCase().replace("*", "").replace(" ", "");
        return n.contains("ST");
    }

    /** 涨幅接近板块涨停，先不拉 K 线。 */
    public static boolean limitUpHint(String code, Double changePct) {
        if (changePct == null || !Double.isFinite(changePct)) {
            return false;
        }
        String c = code == null ? "" : code;
        double cap = c.startsWith("300") || c.startsWith("301") || c.startsWith("688") ? 19.5 : 9.5;
        return changePct >= cap;
    }

    /** 更新时间落在跳过窗口内。 */
    public static boolean recentlyTouched(LocalDateTime updatedAt, int skipDays, LocalDateTime now) {
        if (updatedAt == null || skipDays <= 0 || now == null) {
            return false;
        }
        return !updatedAt.isBefore(now.minusDays(skipDays));
    }
}
