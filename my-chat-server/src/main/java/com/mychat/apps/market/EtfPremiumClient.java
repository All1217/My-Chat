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
 * 东财 ETF 溢价：按代码查 ulist 的 IOPV(f441)；列表没有现价时用调用方已有的价格。
 * 无 IOPV 时降级最近净值。同一轮可复用 {@link #newSession()}，每只代码只请求一次。
 * clist 的 pz 实际最多 100 且按涨跌幅排序，不能靠拉全表找指定代码。
 */
@Slf4j
@Component
public class EtfPremiumClient {

    private static final String ULIST_URL =
            "https://push2delay.eastmoney.com/api/qt/ulist.np/get"
                    + "?fltt=2&np=1&fields=f12,f14,f2,f402,f441&secids=";

    private static final String NAV_URL =
            "https://api.fund.eastmoney.com/f10/lsjz"
                    + "?fundCode=%s&pageIndex=1&pageSize=5&startDate=&endDate=";

    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    /** 开启一轮查询会话（同一代码不重复请求）。 */
    public Session newSession() {
        return new Session();
    }

    /** 一轮内的溢价查询（每只代码只请求一次）。 */
    public final class Session {
        private final Map<String, ClistRow> cache = new HashMap<>();

        /** 按代码取溢价；失败返回 null（调用方 fail-closed）。 */
        public PremiumQuote lookup(String symbol) {
            return lookup(symbol, null);
        }

        /**
         * 按代码取溢价。knownPrice 是行情里已经拿到的价格，列表现价缺失时用它。
         * 失败返回 null（调用方 fail-closed）。
         */
        public PremiumQuote lookup(String symbol, Double knownPrice) {
            if (!StringUtils.hasText(symbol)) {
                return null;
            }
            String code = symbol.trim().toUpperCase(Locale.ROOT);
            ClistRow row = rowOf(code);
            Double listPrice = row == null ? null : row.price;
            Double iopv = row == null ? null : row.iopv;
            Double price = firstPositive(listPrice, knownPrice);
            // 价和 IOPV 都在时不必再打净值接口
            Double nav = positive(price) != null && positive(iopv) != null ? null : fetchLatestNav(code);
            PremiumQuote quote = quoteOf(code, row == null ? null : row.name, listPrice, iopv,
                    row == null ? null : row.discountPct, knownPrice, nav);
            if (quote == null) {
                log.warn("ETF 溢价不可得 {}: 列表价={} 已知价={} IOPV={} 净值={}",
                        code, listPrice, knownPrice, iopv, nav);
            }
            return quote;
        }

        /** 按 secid 查这一只；失败记一条空行，避免同一轮重复请求。 */
        private ClistRow rowOf(String code) {
            if (cache.containsKey(code)) {
                return cache.get(code);
            }
            ClistRow row = fetchUlist(code);
            cache.put(code, row);
            return row;
        }

        private ClistRow fetchUlist(String code) {
            String secid;
            try {
                secid = MarketSymbolParser.parse(code).eastMoneySecId();
            } catch (IllegalArgumentException e) {
                return null;
            }
            if (!StringUtils.hasText(secid)) {
                return null;
            }
            try {
                Request req = new Request.Builder()
                        .url(ULIST_URL + secid)
                        .header("User-Agent", "Mozilla/5.0")
                        .header("Referer", "https://quote.eastmoney.com/")
                        .get()
                        .build();
                try (Response resp = http.newCall(req).execute()) {
                    if (!resp.isSuccessful() || resp.body() == null) {
                        log.warn("ETF ulist HTTP {} {}", code, resp.code());
                        return null;
                    }
                    JsonNode diff = mapper.readTree(resp.body().string()).path("data").path("diff");
                    JsonNode node = firstRow(diff);
                    if (node == null) {
                        return null;
                    }
                    ClistRow row = new ClistRow();
                    row.name = text(node, "f14");
                    row.price = num(node, "f2");
                    row.discountPct = num(node, "f402");
                    row.iopv = num(node, "f441");
                    return row;
                }
            } catch (IOException e) {
                log.warn("ETF ulist 拉取失败 {}: {}", code, e.getMessage());
                return null;
            }
        }
    }

    /** 用列表价或已知价对 IOPV；都没有 IOPV 时用最近净值。算不出则返回 null。 */
    static PremiumQuote quoteOf(String code, String name, Double listPrice, Double iopv, Double discountPct,
                                 Double knownPrice, Double nav) {
        Double price = firstPositive(listPrice, knownPrice);
        Double liveIopv = positive(iopv);
        if (price != null && liveIopv != null) {
            PremiumQuote q = new PremiumQuote();
            q.setSymbol(code);
            q.setName(name);
            q.setPrice(price);
            q.setIopv(liveIopv);
            q.setPremiumBasis("IOPV");
            q.setPremiumPct(round2((price - liveIopv) / liveIopv * 100.0));
            if (discountPct != null) {
                q.setEastMoneyDiscountPct(discountPct);
            }
            return q;
        }
        Double liveNav = positive(nav);
        if (price == null || liveNav == null) {
            return null;
        }
        PremiumQuote q = new PremiumQuote();
        q.setSymbol(code);
        q.setName(name);
        q.setPrice(price);
        q.setNav(liveNav);
        q.setPremiumBasis("NAV_DELAYED");
        q.setPremiumPct(round2((price - liveNav) / liveNav * 100.0));
        return q;
    }

    /** diff 可能是数组，也可能是按下标展开的对象。 */
    private static JsonNode firstRow(JsonNode diff) {
        if (diff == null || diff.isNull() || diff.isMissingNode()) {
            return null;
        }
        if (diff.isArray()) {
            return diff.isEmpty() ? null : diff.get(0);
        }
        if (diff.isObject()) {
            var it = diff.fields();
            return it.hasNext() ? it.next().getValue() : null;
        }
        return null;
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

    /** 合法报价：有限、大于 0，并排除东财缺失字段时的超大哨兵值。 */
    private static Double positive(Double v) {
        if (v == null || !Double.isFinite(v) || v <= 0 || v >= 100_000d) {
            return null;
        }
        return v;
    }

    private static Double firstPositive(Double primary, Double fallback) {
        Double a = positive(primary);
        return a != null ? a : positive(fallback);
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
