package com.mychat.apps.market;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mychat.apps.market.entity.vo.KlinePointVO;
import com.mychat.apps.market.entity.vo.MarketQuoteVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;

import java.net.URI;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 拉取 A 股与美股 K 线。美股只走东方财富；A 股按东财前复权、腾讯前复权、新浪不复权顺序兜底。
 */
@Component
public class MarketQuoteClient {

    private static final Logger log = LoggerFactory.getLogger(MarketQuoteClient.class);
    private static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";
    private static final String EAST_MONEY_REFERER = "https://quote.eastmoney.com/";
    private static final String TENCENT_REFERER = "https://gu.qq.com/";
    private static final String SINA_REFERER = "https://finance.sina.com.cn/";
    private static final long MIN_GAP_MILLIS = 400L;
    private static final long CACHE_TTL_MILLIS = 180_000L;
    private static final int MAX_ATTEMPTS = 3;

    private final RestClient restClient;
    private final ObjectMapper mapper;
    private final OkHttpClient okHttp;
    /** 串行出站，避免搜集线程同时打东财和兜底源。 */
    private final Object gate = new Object();
    private final Map<String, CachedQuote> cache = new ConcurrentHashMap<>();
    private long nextRequestAt;
    /** 最近一次 OkHttp 失败是否为掐流。仅在 gate 内读写。 */
    private boolean streamCut;

    /** 成功行情的短缓存。失败不放入。 */
    private record CachedQuote(MarketQuoteVO quote, long expiresAt) {
    }

    /** 生产构造：OkHttp 拉行情（curl 通、JDK HttpClient 对本站会空响应头）。 */
    @Autowired
    public MarketQuoteClient() {
        this.restClient = null;
        this.mapper = new ObjectMapper();
        this.okHttp = new OkHttpClient.Builder()
                .protocols(List.of(Protocol.HTTP_1_1))
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .callTimeout(20, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .connectionPool(new ConnectionPool(5, 5, TimeUnit.SECONDS))
                .build();
    }

    /** 测试构造：注入已绑定 Mock 的 RestClient，不限速、不兜底。 */
    MarketQuoteClient(RestClient restClient, ObjectMapper mapper) {
        this.restClient = restClient;
        this.mapper = mapper;
        this.okHttp = null;
    }

    /** 按代码和时间段取行情；失败抛 IllegalArgumentException。 */
    public MarketQuoteVO fetch(String rawSymbol, String rangeKey) {
        String range = MarketCalendar.requireRange(rangeKey);
        ResolvedSymbol symbol = MarketSymbolParser.parse(rawSymbol);
        MarketQuoteVO cached = readCache(symbol, range);
        if (cached != null) {
            return cached;
        }
        if (okHttp == null) {
            MarketQuoteVO quote = symbol.cn() ? fetchCn(symbol, range) : fetchUs(symbol, range);
            return finish(symbol, range, quote);
        }
        synchronized (gate) {
            MarketQuoteVO again = readCache(symbol, range);
            if (again != null) {
                return again;
            }
            return finish(symbol, range, loadPaced(symbol, range));
        }
    }

    /** 根数不足则抛出；成功结果缓存约 3 分钟。 */
    private MarketQuoteVO finish(ResolvedSymbol symbol, String range, MarketQuoteVO quote) {
        if (quote.getHistory() == null || quote.getHistory().size() < 8) {
            throw new IllegalArgumentException("该时间段内行情过少，请换一只股票或更长时间段");
        }
        fillStats(quote);
        writeCache(symbol, range, quote);
        return quote;
    }

    /**
     * 生产路径：东财掐流后 A 股改腾讯前复权，再改新浪不复权。美股不兜底。
     */
    private MarketQuoteVO loadPaced(ResolvedSymbol symbol, String range) {
        try {
            MarketQuoteVO quote = symbol.cn() ? fetchCn(symbol, range) : fetchUs(symbol, range);
            log.info("行情来源 source=eastmoney symbol={} range={}", symbol.symbol(), range);
            return quote;
        } catch (IllegalArgumentException ex) {
            if (!symbol.cn() || !streamCut) {
                throw ex;
            }
            // 1. 东财连接被掐断，改拉腾讯前复权日 K
            log.warn("东财行情被掐断，改拉腾讯 symbol={}", symbol.symbol());
            try {
                MarketQuoteVO quote = fetchTencent(symbol, range);
                if (quote.getHistory() == null || quote.getHistory().size() < 8) {
                    throw new IllegalArgumentException("无法识别或暂无行情");
                }
                log.info("行情来源 source=tencent-qfq symbol={} range={}", symbol.symbol(), range);
                return quote;
            } catch (IllegalArgumentException tencentEx) {
                // 2. 腾讯也失败，最后用新浪不复权日 K
                log.warn("腾讯行情失败，改拉新浪 symbol={}: {}", symbol.symbol(), tencentEx.getMessage());
                MarketQuoteVO quote = fetchSina(symbol, range);
                log.info("行情来源 source=sina-raw symbol={} range={}", symbol.symbol(), range);
                return quote;
            }
        }
    }

    /** 东方财富日 K 或周 K。 */
    private MarketQuoteVO fetchCn(ResolvedSymbol symbol, String range) {
        LocalDate cutoff = MarketCalendar.cutoffDate(range);
        String beg = cutoff == null ? "0" : cutoff.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        URI uri = UriComponentsBuilder
                .fromUriString("https://push2his.eastmoney.com/api/qt/stock/kline/get")
                .queryParam("fields1", "f1,f2,f3,f4,f5,f6,f7,f8,f9,f10,f11,f12,f13")
                .queryParam("fields2", "f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61")
                .queryParam("beg", beg)
                .queryParam("end", "20500000")
                .queryParam("ut", "fa5fd1943c7b386f172d6893dbfba10b")
                .queryParam("rtntype", "6")
                .queryParam("secid", symbol.eastMoneySecId())
                .queryParam("klt", MarketCalendar.eastMoneyKlt(range))
                .queryParam("fqt", "1")
                .queryParam("lmt", MarketCalendar.eastMoneyLimit(range))
                .build(true)
                .toUri();
        JsonNode root = getJson(uri, EAST_MONEY_REFERER);
        JsonNode data = root.path("data");
        if (data.isMissingNode() || data.isNull()) {
            throw new IllegalArgumentException("无法识别或暂无行情");
        }
        JsonNode klines = data.path("klines");
        if (!klines.isArray() || klines.isEmpty()) {
            throw new IllegalArgumentException("无法识别或暂无行情");
        }
        List<KlinePointVO> bars = new ArrayList<>();
        for (JsonNode node : klines) {
            bars.add(parseEastMoneyBar(node.asText()));
        }
        return fromBars(symbol, range, textOr(data.path("name"), symbol.symbol()), bars);
    }

    /** 美股走东方财富（100 指数 / 105 NASDAQ / 106 NYSE / 107 AMEX）。 */
    private MarketQuoteVO fetchUs(ResolvedSymbol symbol, String range) {
        IllegalArgumentException last = null;
        for (String mkt : new String[] {"100", "105", "106", "107"}) {
            try {
                ResolvedSymbol em = new ResolvedSymbol(
                        symbol.symbol(), "US", mkt + "." + symbol.symbol());
                return fetchCn(em, range);
            } catch (IllegalArgumentException e) {
                last = e;
            }
        }
        throw last != null ? last : new IllegalArgumentException("无法识别或暂无行情");
    }

    /** 腾讯前复权日 K。价格口径对应东财 fqt=1；成交额与换手留空。 */
    private MarketQuoteVO fetchTencent(ResolvedSymbol symbol, String range) {
        String prefixed = cnVendorPrefix(symbol) + symbol.symbol();
        URI uri = URI.create("https://web.ifzq.gtimg.cn/appstock/app/fqkline/get?param="
                + prefixed + ",day,,," + fallbackBars(range) + ",qfq");
        JsonNode root = getJson(uri, TENCENT_REFERER);
        List<KlinePointVO> bars = parseTencentBars(root, prefixed);
        if (bars.isEmpty()) {
            throw new IllegalArgumentException("无法识别或暂无行情");
        }
        return fromBars(symbol, range, symbol.symbol(), bars);
    }

    /** 新浪不复权日 K，只作最后退路。分红送转后价格与前复权不一致。 */
    private MarketQuoteVO fetchSina(ResolvedSymbol symbol, String range) {
        String prefixed = cnVendorPrefix(symbol) + symbol.symbol();
        URI uri = URI.create("https://money.finance.sina.com.cn/quotes_service/api/json_v2.php/"
                + "CN_MarketData.getKLineData?symbol=" + prefixed
                + "&scale=240&ma=no&datalen=" + fallbackBars(range));
        JsonNode root = getJson(uri, SINA_REFERER);
        List<KlinePointVO> bars = parseSinaBars(root);
        if (bars.isEmpty()) {
            throw new IllegalArgumentException("无法识别或暂无行情");
        }
        return fromBars(symbol, range, symbol.symbol(), bars);
    }

    /** 按时间段丢掉过早的点，拼成行情视图。 */
    private MarketQuoteVO fromBars(ResolvedSymbol symbol, String range, String name, List<KlinePointVO> bars) {
        LocalDate cutoff = MarketCalendar.cutoffDate(range);
        List<KlinePointVO> history = new ArrayList<>();
        for (KlinePointVO point : bars) {
            if (keepBar(point, cutoff)) {
                history.add(point);
            }
        }
        MarketQuoteVO quote = new MarketQuoteVO();
        quote.setSymbol(symbol.symbol());
        quote.setMarket(symbol.market());
        quote.setName(name);
        quote.setRangeKey(range);
        quote.setHistory(history);
        return quote;
    }

    /** GET JSON。生产走 OkHttp；单测走注入的 RestClient。 */
    private JsonNode getJson(URI uri, String referer) {
        if (okHttp != null) {
            return getJsonOkHttp(uri, referer);
        }
        return getJsonRest(uri, referer);
    }

    /** 最多 3 次。掐流时清连接池，并以 1s、2s 退避；每次出站另留 400ms 间隔。 */
    private JsonNode getJsonOkHttp(URI uri, String referer) {
        Exception last = null;
        streamCut = false;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            // 1. 与上一次出站至少隔 400ms，兜底请求也算
            awaitGap();
            Request request = new Request.Builder()
                    .url(uri.toString())
                    .header("User-Agent", UA)
                    .header("Referer", referer)
                    .get()
                    .build();
            try (Response response = okHttp.newCall(request).execute()) {
                String body = response.body() == null ? "" : response.body().string();
                if (!response.isSuccessful() || body.isBlank()) {
                    throw new IllegalArgumentException("无法识别或暂无行情");
                }
                return mapper.readTree(body);
            } catch (IllegalArgumentException e) {
                throw e;
            } catch (Exception e) {
                last = e;
                // 2. 流被掐断则丢弃坏连接，并允许 A 股改走下一数据源
                if (streamReset(e)) {
                    streamCut = true;
                    okHttp.connectionPool().evictAll();
                }
                if (attempt >= MAX_ATTEMPTS) {
                    break;
                }
                // 3. 退避后再试：1s、2s
                sleepQuietly(1000L * attempt);
            }
        }
        log.warn("拉取行情失败 uri={}: {}", uri, last == null ? "" : last.getMessage());
        throw new IllegalArgumentException("无法识别或暂无行情");
    }

    /** 相邻两次出站至少间隔 400ms。调用方已持有 gate。 */
    private void awaitGap() {
        long wait = nextRequestAt - System.currentTimeMillis();
        if (wait > 0) {
            sleepQuietly(wait);
        }
        nextRequestAt = System.currentTimeMillis() + MIN_GAP_MILLIS;
    }

    /** 重试等待；打断则恢复中断标记。 */
    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    /** 连接被掐断。清掉连接池后再请求，避免后面的代码沿用坏连接。 */
    private static boolean streamReset(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains("unexpected end of stream")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private JsonNode getJsonRest(URI uri, String referer) {
        try {
            String body = restClient.get()
                    .uri(uri)
                    .accept(MediaType.APPLICATION_JSON)
                    .header("User-Agent", UA)
                    .header("Referer", referer)
                    .retrieve()
                    .body(String.class);
            if (body == null || body.isBlank()) {
                throw new IllegalArgumentException("无法识别或暂无行情");
            }
            return mapper.readTree(body);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.warn("拉取行情失败 uri={}: {}", uri, e.getMessage());
            throw new IllegalArgumentException("无法识别或暂无行情");
        }
    }

    /** 命中未过期缓存则返回，否则 null。 */
    private MarketQuoteVO readCache(ResolvedSymbol symbol, String range) {
        String key = cacheKey(symbol, range);
        CachedQuote hit = cache.get(key);
        if (hit == null) {
            return null;
        }
        if (hit.expiresAt() <= System.currentTimeMillis()) {
            cache.remove(key);
            return null;
        }
        return hit.quote();
    }

    /** 写入成功行情。调用方保证 history 已够根数。 */
    private void writeCache(ResolvedSymbol symbol, String range, MarketQuoteVO quote) {
        cache.put(cacheKey(symbol, range), new CachedQuote(quote, System.currentTimeMillis() + CACHE_TTL_MILLIS));
    }

    private static String cacheKey(ResolvedSymbol symbol, String range) {
        return symbol.market() + ":" + symbol.symbol() + ":" + range;
    }

    /** 沪 sh、深 sz、京（4/8 开头）bj。 */
    static String cnVendorPrefix(ResolvedSymbol symbol) {
        String code = symbol.symbol();
        if (code.startsWith("4") || code.startsWith("8")) {
            return "bj";
        }
        String secId = symbol.eastMoneySecId();
        if (secId != null && secId.startsWith("1.")) {
            return "sh";
        }
        return "sz";
    }

    /** 兜底源只有日 K。长周期东财用周线，这里按约 5 倍根数补窗口，上限 2000。 */
    private static int fallbackBars(String range) {
        int limit = MarketCalendar.eastMoneyLimit(range);
        if ("101".equals(MarketCalendar.eastMoneyKlt(range))) {
            return limit;
        }
        return Math.min(limit * 5, 2000);
    }

    /** 有限档丢掉早于 cutoff 的点；上市以来 cutoff 为空。 */
    static boolean keepBar(KlinePointVO point, LocalDate cutoff) {
        if (point == null || point.getDate() == null) {
            return false;
        }
        if (cutoff == null) {
            return true;
        }
        return !LocalDate.parse(point.getDate()).isBefore(cutoff);
    }

    /** 解析东方财富 kline 字符串。 */
    static KlinePointVO parseEastMoneyBar(String csv) {
        if (csv == null || csv.isBlank()) {
            return null;
        }
        String[] parts = csv.split(",");
        if (parts.length < 6) {
            return null;
        }
        try {
            KlinePointVO point = new KlinePointVO();
            point.setDate(parts[0]);
            point.setOpen(Double.parseDouble(parts[1]));
            point.setClose(Double.parseDouble(parts[2]));
            point.setHigh(Double.parseDouble(parts[3]));
            point.setLow(Double.parseDouble(parts[4]));
            point.setVolume(Math.round(Double.parseDouble(parts[5])));
            // f57 成交额在第 7 列；旧数据或不足 7 列时保持空。
            if (parts.length > 6 && !parts[6].isBlank()) {
                point.setAmount(Double.parseDouble(parts[6]));
            }
            // f61 换手率在第 11 列。
            if (parts.length > 10 && !parts[10].isBlank()) {
                point.setTurnoverRate(Double.parseDouble(parts[10]));
            }
            return point;
        } catch (Exception e) {
            return null;
        }
    }

    /** 解析腾讯 qfqday：日期、开、收、高、低、量。 */
    static List<KlinePointVO> parseTencentBars(JsonNode root, String prefixed) {
        JsonNode rows = root.path("data").path(prefixed).path("qfqday");
        if (!rows.isArray() || rows.isEmpty()) {
            rows = root.path("data").path(prefixed).path("day");
        }
        List<KlinePointVO> bars = new ArrayList<>();
        if (!rows.isArray()) {
            return bars;
        }
        for (JsonNode row : rows) {
            if (!row.isArray() || row.size() < 6) {
                continue;
            }
            KlinePointVO point = bar(
                    row.get(0).asText(),
                    row.get(1).asText(),
                    row.get(2).asText(),
                    row.get(3).asText(),
                    row.get(4).asText(),
                    row.get(5).asText());
            if (point != null) {
                bars.add(point);
            }
        }
        return bars;
    }

    /** 解析新浪日 K 数组。字段顺序是开、高、低、收。 */
    static List<KlinePointVO> parseSinaBars(JsonNode root) {
        List<KlinePointVO> bars = new ArrayList<>();
        if (root == null || !root.isArray()) {
            return bars;
        }
        for (JsonNode node : root) {
            KlinePointVO point = parseSinaBar(node);
            if (point != null) {
                bars.add(point);
            }
        }
        return bars;
    }

    /** 解析新浪单根。不复权，成交额与换手留空。 */
    static KlinePointVO parseSinaBar(JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        return bar(
                node.path("day").asText(),
                node.path("open").asText(),
                node.path("close").asText(),
                node.path("high").asText(),
                node.path("low").asText(),
                node.path("volume").asText());
    }

    /** 六元组拼一根 K 线。开、收、高、低、量；无法解析则 null。 */
    private static KlinePointVO bar(String date, String open, String close, String high, String low, String volume) {
        if (date == null || date.isBlank()) {
            return null;
        }
        try {
            KlinePointVO point = new KlinePointVO();
            point.setDate(date);
            point.setOpen(Double.parseDouble(open));
            point.setClose(Double.parseDouble(close));
            point.setHigh(Double.parseDouble(high));
            point.setLow(Double.parseDouble(low));
            point.setVolume(Math.round(Double.parseDouble(volume)));
            return point;
        } catch (Exception e) {
            return null;
        }
    }

    /** 用 K 线填最新价、涨跌幅、区间高低、成交额与换手。 */
    static void fillStats(MarketQuoteVO quote) {
        List<KlinePointVO> history = quote.getHistory();
        KlinePointVO last = history.get(history.size() - 1);
        quote.setLastPrice(last.getClose());
        quote.setVolume(last.getVolume());
        quote.setAmount(last.getAmount());
        quote.setTurnoverRate(last.getTurnoverRate());
        if (history.size() >= 2) {
            Double prev = history.get(history.size() - 2).getClose();
            if (prev != null && prev != 0 && last.getClose() != null) {
                quote.setChangePct((last.getClose() - prev) / prev * 100.0);
            }
        }
        double high = Double.NEGATIVE_INFINITY;
        double low = Double.POSITIVE_INFINITY;
        for (KlinePointVO bar : history) {
            if (bar.getHigh() != null) {
                high = Math.max(high, bar.getHigh());
            } else if (bar.getClose() != null) {
                high = Math.max(high, bar.getClose());
            }
            if (bar.getLow() != null) {
                low = Math.min(low, bar.getLow());
            } else if (bar.getClose() != null) {
                low = Math.min(low, bar.getClose());
            }
        }
        if (high != Double.NEGATIVE_INFINITY) {
            quote.setHigh(high);
        }
        if (low != Double.POSITIVE_INFINITY) {
            quote.setLow(low);
        }
    }

    private static String textOr(JsonNode node, String fallback) {
        String t = node.asText("");
        return t.isBlank() ? fallback : t;
    }

}
