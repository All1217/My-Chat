package com.mychat.apps.market;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 东财 ETF 溢价：主源 clist IOPV(f441)+折价率(f402)；无 IOPV 时降级最近净值。
 * 同一轮可复用 {@link #newSession()} 缓存，避免按代码重复拉全表。
 */
@Slf4j
@Component
public class EtfPremiumClient {

    private static final String CLIST_URL =
            "https://push2delay.eastmoney.com/api/qt/clist/get"
                    + "?pn=1&pz=5000&po=1&np=1&fltt=2&invt=2&fid=f3"
                    + "&fs=b:MK0021,b:MK0022,b:MK0023,b:MK0024"
                    + "&fields=f12,f14,f2,f402,f441";

    private static final String NAV_URL =
            "https://api.fund.eastmoney.com/f10/lsjz"
                    + "?fundCode=%s&pageIndex=1&pageSize=5&startDate=&endDate=";

    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    /** 开启一轮查询会话（缓存 ETF 列表）。 */
    public Session newSession() {
        return new Session();
    }

    /** 一轮内的溢价查询（列表只拉一次）。 */
    public final class Session {
        private Map<String, ClistRow> cache;

        /** 按代码取溢价；失败返回 null（调用方 fail-closed）。 */
        public PremiumQuote lookup(String symbol) {
            if (!StringUtils.hasText(symbol)) {
                return null;
            }
            String code = symbol.trim().toUpperCase(Locale.ROOT);
            ensureCache();
            ClistRow row = cache.get(code);
            if (row != null && row.iopv != null && row.iopv > 0 && row.price != null && row.price > 0) {
                PremiumQuote q = new PremiumQuote();
                q.setSymbol(code);
                q.setName(row.name);
                q.setPrice(row.price);
                q.setIopv(row.iopv);
                q.setPremiumBasis("IOPV");
                // 东财 f402 为折价率(%)：溢价 ≈ -折价；若同时有 IOPV 则以价差为准
                double premium = (row.price - row.iopv) / row.iopv * 100.0;
                q.setPremiumPct(round2(premium));
                if (row.discountPct != null) {
                    q.setEastMoneyDiscountPct(row.discountPct);
                }
                return q;
            }
            // 备源：最近单位净值
            Double nav = fetchLatestNav(code);
            Double price = row != null ? row.price : null;
            if (price == null || price <= 0) {
                return null;
            }
            if (nav == null || nav <= 0) {
                return null;
            }
            PremiumQuote q = new PremiumQuote();
            q.setSymbol(code);
            q.setName(row != null ? row.name : null);
            q.setPrice(price);
            q.setNav(nav);
            q.setPremiumBasis("NAV_DELAYED");
            q.setPremiumPct(round2((price - nav) / nav * 100.0));
            return q;
        }

        private void ensureCache() {
            if (cache != null) {
                return;
            }
            cache = new HashMap<>();
            try {
                Request req = new Request.Builder()
                        .url(CLIST_URL)
                        .header("User-Agent", "Mozilla/5.0")
                        .header("Referer", "https://quote.eastmoney.com/")
                        .get()
                        .build();
                try (Response resp = http.newCall(req).execute()) {
                    if (!resp.isSuccessful() || resp.body() == null) {
                        log.warn("ETF clist HTTP {}", resp.code());
                        return;
                    }
                    JsonNode diff = mapper.readTree(resp.body().string()).path("data").path("diff");
                    if (!diff.isArray()) {
                        return;
                    }
                    for (JsonNode n : diff) {
                        String code = text(n, "f12");
                        if (!StringUtils.hasText(code)) {
                            continue;
                        }
                        ClistRow row = new ClistRow();
                        row.name = text(n, "f14");
                        row.price = num(n, "f2");
                        row.discountPct = num(n, "f402");
                        row.iopv = num(n, "f441");
                        cache.put(code.toUpperCase(Locale.ROOT), row);
                    }
                }
            } catch (IOException e) {
                log.warn("ETF clist 拉取失败: {}", e.getMessage());
            }
        }
    }

    private Double fetchLatestNav(String fundCode) {
        try {
            Request req = new Request.Builder()
                    .url(String.format(NAV_URL, fundCode))
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Referer", "https://fundf10.eastmoney.com/")
                    .get()
                    .build();
            try (Response resp = http.newCall(req).execute()) {
                if (!resp.isSuccessful() || resp.body() == null) {
                    return null;
                }
                JsonNode list = mapper.readTree(resp.body().string())
                        .path("Data").path("LSJZList");
                if (!list.isArray() || list.isEmpty()) {
                    return null;
                }
                return num(list.get(0), "DWJZ");
            }
        } catch (Exception e) {
            log.debug("净值拉取失败 {}: {}", fundCode, e.getMessage());
            return null;
        }
    }

    /** 简单判断是否场内 ETF/基金（代码启发式）。 */
    public static boolean looksLikeEtf(String symbol, String name) {
        if (name != null) {
            String n = name.toUpperCase(Locale.ROOT);
            if (n.contains("ETF") || n.contains("LOF") || n.contains("QDII")) {
                return true;
            }
        }
        if (!StringUtils.hasText(symbol)) {
            return false;
        }
        String s = symbol.trim();
        if (s.length() != 6 || !s.chars().allMatch(Character::isDigit)) {
            return false;
        }
        // 5xxxxx / 15xxxx / 16xxxx 常见场内基金
        return s.startsWith("5") || s.startsWith("15") || s.startsWith("16");
    }

    /** 名称或代码含跨境/QDII 迹象。 */
    public static boolean looksLikeQdii(String symbol, String name) {
        if (name != null) {
            String n = name.toUpperCase(Locale.ROOT);
            if (n.contains("QDII") || n.contains("纳指") || n.contains("标普") || n.contains("恒生")
                    || n.contains("日经") || n.contains("原油") || n.contains("黄金")) {
                return true;
            }
        }
        if (!StringUtils.hasText(symbol)) {
            return false;
        }
        String s = symbol.trim();
        return s.startsWith("513") || s.startsWith("1599") || s.startsWith("1595") || s.startsWith("1647");
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        if (v == null || v.isNull() || v.isMissingNode()) {
            return null;
        }
        String s = v.asText();
        return StringUtils.hasText(s) && !"-".equals(s) ? s : null;
    }

    private static Double num(JsonNode n, String field) {
        JsonNode v = n.get(field);
        if (v == null || v.isNull() || v.isMissingNode()) {
            return null;
        }
        if (v.isNumber()) {
            double d = v.asDouble();
            return Double.isFinite(d) ? d : null;
        }
        String s = v.asText();
        if (!StringUtils.hasText(s) || "-".equals(s)) {
            return null;
        }
        try {
            double d = Double.parseDouble(s);
            return Double.isFinite(d) ? d : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static double round2(double v) {
        return BigDecimal.valueOf(v).setScale(4, RoundingMode.HALF_UP).doubleValue();
    }

    private static final class ClistRow {
        String name;
        Double price;
        Double iopv;
        Double discountPct;
    }

    /** 溢价报价结果。 */
    @Data
    public static class PremiumQuote {
        private String symbol;
        private String name;
        private Double price;
        private Double iopv;
        private Double nav;
        /** IOPV 或 NAV_DELAYED */
        private String premiumBasis;
        /** 溢价率 %：正数为溢价 */
        private Double premiumPct;
        private Double eastMoneyDiscountPct;
    }
}
