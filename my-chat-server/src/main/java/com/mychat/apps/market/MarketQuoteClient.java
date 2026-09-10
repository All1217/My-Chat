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
import okhttp3.Request;
import okhttp3.Response;

import java.net.URI;
import java.util.concurrent.TimeUnit;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 拉取 A 股与美股日 K（均走东方财富），拼成统一行情视图。
 */
@Component
public class MarketQuoteClient {

    private static final Logger log = LoggerFactory.getLogger(MarketQuoteClient.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);
    private static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";

    private final RestClient restClient;
    private final ObjectMapper mapper;
    private final OkHttpClient okHttp;

    /** 生产构造：OkHttp 拉行情（curl 通、JDK HttpClient 对本站会空响应头）。 */
    @Autowired
    public MarketQuoteClient() {
        this.restClient = null;
        this.mapper = new ObjectMapper();
        this.okHttp = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .callTimeout(20, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .connectionPool(new ConnectionPool(5, 5, TimeUnit.SECONDS))
                .build();
    }

    /** 测试构造：注入已绑定 Mock 的 RestClient。 */
    MarketQuoteClient(RestClient restClient, ObjectMapper mapper) {
        this.restClient = restClient;
        this.mapper = mapper;
        this.okHttp = null;
    }

    /** 按代码和时间段取行情；失败抛 IllegalArgumentException。 */
    public MarketQuoteVO fetch(String rawSymbol, String rangeKey) {
        String range = MarketCalendar.requireRange(rangeKey);
        ResolvedSymbol symbol = MarketSymbolParser.parse(rawSymbol);
        MarketQuoteVO quote = symbol.cn() ? fetchCn(symbol, range) : fetchUs(symbol, range);
        if (quote.getHistory() == null || quote.getHistory().size() < 8) {
            throw new IllegalArgumentException("该时间段内行情过少，请换一只股票或更长时间段");
        }
        fillStats(quote);
        return quote;
    }

    /** 东方财富日 K。 */
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
        JsonNode root = getJson(uri);
        JsonNode data = root.path("data");
        if (data.isMissingNode() || data.isNull()) {
            throw new IllegalArgumentException("无法识别或暂无行情");
        }
        JsonNode klines = data.path("klines");
        if (!klines.isArray() || klines.isEmpty()) {
            throw new IllegalArgumentException("无法识别或暂无行情");
        }
        List<KlinePointVO> history = new ArrayList<>();
        for (JsonNode node : klines) {
            KlinePointVO point = parseEastMoneyBar(node.asText());
            if (keepBar(point, cutoff)) {
                history.add(point);
            }
        }
        MarketQuoteVO quote = new MarketQuoteVO();
        quote.setSymbol(symbol.symbol());
        quote.setMarket(symbol.market());
        quote.setName(textOr(data.path("name"), symbol.symbol()));
        quote.setRangeKey(range);
        quote.setHistory(history);
        return quote;
    }

    /** 美股走东方财富（100 指数 / 105 NASDAQ / 106 NYSE / 107 AMEX）。 */
    private MarketQuoteVO fetchUs(ResolvedSymbol symbol, String range) {
        IllegalArgumentException last = null;
        for (String mkt : new String[] {"100", "105", "106", "107"}) {
            try {
                ResolvedSymbol em = new ResolvedSymbol(
                        symbol.symbol(), "US", mkt + "." + symbol.symbol());
                MarketQuoteVO quote = fetchCn(em, range);
                return quote;
            } catch (IllegalArgumentException e) {
                last = e;
            }
        }
        throw last != null ? last : new IllegalArgumentException("无法识别或暂无行情");
    }

    /** GET JSON。生产走 OkHttp；单测走注入的 RestClient。 */
    private JsonNode getJson(URI uri) {
        if (okHttp != null) {
            return getJsonOkHttp(uri);
        }
        return getJsonRest(uri);
    }

    private JsonNode getJsonOkHttp(URI uri) {
        Exception last = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            Request request = new Request.Builder()
                    .url(uri.toString())
                    .header("User-Agent", UA)
                    .header("Referer", "https://quote.eastmoney.com/")
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
                if (attempt == 1) {
                    continue;
                }
                log.warn("拉取行情失败 uri={}: {}", uri, e.getMessage());
                throw new IllegalArgumentException("无法识别或暂无行情");
            }
        }
        log.warn("拉取行情失败 uri={}: {}", uri, last == null ? "" : last.getMessage());
        throw new IllegalArgumentException("无法识别或暂无行情");
    }

    private JsonNode getJsonRest(URI uri) {
        try {
            String body = restClient.get()
                    .uri(uri)
                    .accept(MediaType.APPLICATION_JSON)
                    .header("User-Agent", UA)
                    .header("Referer", "https://quote.eastmoney.com/")
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
            return point;
        } catch (Exception e) {
            return null;
        }
    }

    /** 用 K 线填最新价、涨跌幅、区间高低。 */
    static void fillStats(MarketQuoteVO quote) {
        List<KlinePointVO> history = quote.getHistory();
        KlinePointVO last = history.get(history.size() - 1);
        quote.setLastPrice(last.getClose());
        quote.setVolume(last.getVolume());
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
