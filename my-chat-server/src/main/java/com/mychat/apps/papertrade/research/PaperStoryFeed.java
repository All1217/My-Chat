package com.mychat.apps.papertrade.research;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 东财列表、公告和新闻。只给搜集员用，操盘手不调用。
 */
@Slf4j
@Component
public class PaperStoryFeed {

    private static final String CLIST =
            "https://push2delay.eastmoney.com/api/qt/clist/get"
                    + "?pn=%d&pz=100&po=1&np=1&fltt=2&invt=2&fid=f6"
                    + "&fs=m:0+t:6,m:0+t:80,m:1+t:2,m:1+t:23,b:MK0021,b:MK0022,b:MK0023,b:MK0024"
                    + "&fields=f12,f14,f2,f3,f6";
    private static final String ANN =
            "https://np-anotice-stock.eastmoney.com/api/security/ann"
                    + "?page_size=2&page_index=1&ann_type=A&client_source=web&stock_list=";
    private static final String SINA =
            "https://vip.stock.finance.sina.com.cn/quotes_service/api/json_v2.php/"
                    + "Market_Center.getHQNodeData?page=%d&num=%d&sort=amount&asc=0&node=%s";

    private final OkHttpClient http = new OkHttpClient.Builder()
            .protocols(List.of(Protocol.HTTP_1_1))
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    /** 按成交额排序的一页。东财列表断流时改用新浪，首页两边都空则抛错。 */
    public List<ListRow> page(int pageNo) {
        String eastError = null;
        try {
            String body = get(String.format(CLIST, pageNo), "https://quote.eastmoney.com/");
            List<ListRow> rows = parseList(mapper.readTree(body));
            if (!rows.isEmpty() || pageNo > 1) {
                return rows;
            }
            eastError = "empty_page1";
        } catch (Exception e) {
            eastError = e.getClass().getSimpleName();
            log.warn("ETF/股票列表第 {} 页失败: {}", pageNo, e.getMessage());
        }
        try {
            List<ListRow> rows = sinaPage(pageNo);
            if (rows.isEmpty() && pageNo == 1) {
                throw new IllegalStateException("行情列表为空");
            }
            return rows;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            if (pageNo > 1) {
                return List.of();
            }
            throw new IllegalStateException("行情列表失败: " + eastError, e);
        }
    }

    /** 最近两条公告标题。 */
    public List<SourceItem> announcements(String symbol) {
        try {
            String body = get(ANN + symbol, "https://data.eastmoney.com/");
            return parseAnnouncements(mapper.readTree(body), symbol);
        } catch (Exception e) {
            log.debug("公告失败 {}: {}", symbol, e.getMessage());
            return List.of();
        }
    }

    /** 最近两条新闻标题。失败时返回空列表。 */
    public List<SourceItem> news(String symbol) {
        try {
            String param = """
                    {"uid":"","keyword":"%s","type":["cmsArticleWebOld"],"client":"web","clientType":"web","clientVersion":"curr","param":{"cmsArticleWebOld":{"searchScope":"default","sort":"default","pageIndex":1,"pageSize":2,"preTag":"","postTag":""}}}
                    """.formatted(symbol).replace("\n", "");
            String url = "https://search-api-web.eastmoney.com/search/jsonp?cb=&param="
                    + URLEncoder.encode(param, StandardCharsets.UTF_8);
            String body = get(url, "https://so.eastmoney.com/");
            return parseNews(mapper.readTree(body));
        } catch (Exception e) {
            log.debug("新闻失败 {}: {}", symbol, e.getMessage());
            return List.of();
        }
    }

    /** 解析 clist 的 diff。 */
    static List<ListRow> parseList(JsonNode root) {
        JsonNode diff = root.path("data").path("diff");
        List<JsonNode> rows = new ArrayList<>();
        if (diff.isArray()) {
            diff.forEach(rows::add);
        } else if (diff.isObject()) {
            diff.elements().forEachRemaining(rows::add);
        }
        List<ListRow> out = new ArrayList<>();
        for (JsonNode n : rows) {
            String code = text(n, "f12");
            if (!StringUtils.hasText(code)) {
                continue;
            }
            out.add(new ListRow(code.trim(), text(n, "f14"), num(n, "f2"), num(n, "f3")));
        }
        return out;
    }

    /** 解析公告列表。 */
    static List<SourceItem> parseAnnouncements(JsonNode root, String symbol) {
        JsonNode list = root.path("data").path("list");
        if (!list.isArray()) {
            return List.of();
        }
        List<SourceItem> out = new ArrayList<>();
        for (JsonNode n : list) {
            if (out.size() >= 2) {
                break;
            }
            String title = text(n, "title");
            if (!StringUtils.hasText(title)) {
                continue;
            }
            String art = text(n, "art_code");
            String url = StringUtils.hasText(art)
                    ? "https://data.eastmoney.com/notices/detail/" + symbol + "/" + art + ".html"
                    : null;
            out.add(new SourceItem("ANNOUNCEMENT", title, url, text(n, "notice_date")));
        }
        return out;
    }

    /** 解析新闻搜索。 */
    static List<SourceItem> parseNews(JsonNode root) {
        JsonNode list = root.path("result").path("cmsArticleWebOld");
        if (!list.isArray()) {
            return List.of();
        }
        List<SourceItem> out = new ArrayList<>();
        for (JsonNode n : list) {
            if (out.size() >= 2) {
                break;
            }
            String title = text(n, "title");
            if (!StringUtils.hasText(title)) {
                continue;
            }
            String code = text(n, "code");
            String url = StringUtils.hasText(code) ? "https://finance.eastmoney.com/a/" + code + ".html" : null;
            out.add(new SourceItem("NEWS", title, url, text(n, "date")));
        }
        return out;
    }

    /** 股票和 ETF 各取一页，按成交额合并。 */
    private List<ListRow> sinaPage(int pageNo) throws Exception {
        List<Ranked> ranked = new ArrayList<>();
        Exception last = null;
        for (String node : new String[]{"hs_a", "etf_hq_fund"}) {
            int num = "hs_a".equals(node) ? 80 : 40;
            try {
                String body = get(String.format(SINA, pageNo, num, node), "https://finance.sina.com.cn/");
                appendSina(mapper.readTree(body), ranked);
            } catch (Exception e) {
                last = e;
            }
        }
        if (ranked.isEmpty() && last != null) {
            throw last;
        }
        ranked.sort(Comparator.comparingDouble(Ranked::amount).reversed());
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<ListRow> out = new ArrayList<>();
        for (Ranked row : ranked) {
            if (out.size() >= 100) {
                break;
            }
            if (seen.add(row.row().code())) {
                out.add(row.row());
            }
        }
        return out;
    }

    /** 解析新浪行情数组。空页或 null 当成没有数据。 */
    private static void appendSina(JsonNode root, List<Ranked> into) {
        if (root == null || !root.isArray()) {
            return;
        }
        for (JsonNode n : root) {
            String code = text(n, "code");
            if (!StringUtils.hasText(code)) {
                continue;
            }
            Double amount = num(n, "amount");
            into.add(new Ranked(
                    new ListRow(code.trim(), text(n, "name"), num(n, "trade"), num(n, "changepercent")),
                    amount == null ? 0d : amount));
        }
    }

    private String get(String url, String referer) throws Exception {
        Request req = new Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .header("Referer", referer)
                .get()
                .build();
        try (Response resp = http.newCall(req).execute()) {
            if (!resp.isSuccessful() || resp.body() == null) {
                throw new IllegalStateException("HTTP " + resp.code());
            }
            return resp.body().string();
        }
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        String s = v.asText();
        return StringUtils.hasText(s) && !"-".equals(s) ? s : null;
    }

    private static Double num(JsonNode n, String field) {
        JsonNode v = n.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        if (v.isNumber()) {
            double d = v.asDouble();
            return Double.isFinite(d) ? d : null;
        }
        try {
            double d = Double.parseDouble(v.asText());
            return Double.isFinite(d) ? d : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 列表里的一行。 */
    public record ListRow(String code, String name, Double price, Double changePct) {
    }

    private record Ranked(ListRow row, double amount) {
    }

    /** 一条公告或新闻。 */
    public record SourceItem(String kind, String title, String url, String publishedAt) {
    }
}
