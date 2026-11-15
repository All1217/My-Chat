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
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 宏观/板块短讯（东财要闻与关键词搜索）。每轮共享，失败 fail-soft。
 */
@Slf4j
@Component
public class PaperMacroFeed {

    private static final int MACRO_LIMIT = 8;
    private static final int SECTOR_LIMIT = 3;

    private final OkHttpClient http = new OkHttpClient.Builder()
            .protocols(List.of(Protocol.HTTP_1_1))
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    /** 财经要闻标题池（本轮候选共享）。 */
    public List<PaperStoryFeed.SourceItem> headlines() {
        List<PaperStoryFeed.SourceItem> out = new ArrayList<>();
        try {
            String url = "https://np-listapi.eastmoney.com/comm/web/getNewsByColumns"
                    + "?client=web&biz=web_news_col&column=350&order=1"
                    + "&needInteractData=0&page_index=1&page_size=" + MACRO_LIMIT
                    + "&req_trace=paper-macro";
            String body = get(url, "https://finance.eastmoney.com/");
            out.addAll(parseColumnNews(mapper.readTree(body), MACRO_LIMIT));
        } catch (Exception e) {
            log.debug("宏观要闻失败: {}", e.getMessage());
        }
        if (out.isEmpty()) {
            try {
                String url = "https://feed.mix.sina.com.cn/api/roll/get"
                        + "?pageid=153&lid=2516&k=&num=" + MACRO_LIMIT + "&page=1";
                String body = get(url, "https://finance.sina.com.cn/");
                out.addAll(parseSinaRoll(mapper.readTree(body), MACRO_LIMIT));
            } catch (Exception e) {
                log.debug("新浪要闻失败: {}", e.getMessage());
            }
        }
        return out;
    }

    /** 与名称相关的板块/主题短讯；拿不到则空列表。 */
    public List<PaperStoryFeed.SourceItem> sectorBriefs(String nameHint) {
        if (!StringUtils.hasText(nameHint)) {
            return List.of();
        }
        String keyword = nameHint.trim();
        if (keyword.length() > 12) {
            keyword = keyword.substring(0, 12);
        }
        try {
            String param = """
                    {"uid":"","keyword":"%s","type":["cmsArticleWebOld"],"client":"web","clientType":"web","clientVersion":"curr","param":{"cmsArticleWebOld":{"searchScope":"default","sort":"default","pageIndex":1,"pageSize":%d,"preTag":"","postTag":""}}}
                    """.formatted(keyword, SECTOR_LIMIT).replace("\n", "");
            String url = "https://search-api-web.eastmoney.com/search/jsonp?cb=&param="
                    + URLEncoder.encode(param, StandardCharsets.UTF_8);
            String body = get(url, "https://so.eastmoney.com/");
            return parseSearchNews(mapper.readTree(body), SECTOR_LIMIT);
        } catch (Exception e) {
            log.debug("板块短讯失败 {}: {}", nameHint, e.getMessage());
            return List.of();
        }
    }

    static List<PaperStoryFeed.SourceItem> parseColumnNews(JsonNode root, int limit) {
        JsonNode list = root.path("data").path("list");
        if (!list.isArray()) {
            list = root.path("data").path("news");
        }
        if (!list.isArray()) {
            return List.of();
        }
        List<PaperStoryFeed.SourceItem> out = new ArrayList<>();
        for (JsonNode n : list) {
            if (out.size() >= limit) {
                break;
            }
            String title = firstText(n, "title", "Title");
            if (!StringUtils.hasText(title)) {
                continue;
            }
            String url = firstText(n, "url", "Url", "url_w");
            if (!StringUtils.hasText(url)) {
                String code = firstText(n, "code", "art_code", "newsId");
                if (StringUtils.hasText(code)) {
                    url = "https://finance.eastmoney.com/a/" + code + ".html";
                }
            }
            out.add(new PaperStoryFeed.SourceItem("MACRO", title, url, firstText(n, "showTime", "publishTime", "time")));
        }
        return out;
    }

    static List<PaperStoryFeed.SourceItem> parseSinaRoll(JsonNode root, int limit) {
        JsonNode list = root.path("result").path("data");
        if (!list.isArray()) {
            return List.of();
        }
        List<PaperStoryFeed.SourceItem> out = new ArrayList<>();
        for (JsonNode n : list) {
            if (out.size() >= limit) {
                break;
            }
            String title = firstText(n, "title");
            if (!StringUtils.hasText(title)) {
                continue;
            }
            out.add(new PaperStoryFeed.SourceItem("MACRO", title, firstText(n, "url"), firstText(n, "ctime")));
        }
        return out;
    }

    static List<PaperStoryFeed.SourceItem> parseSearchNews(JsonNode root, int limit) {
        JsonNode list = root.path("result").path("cmsArticleWebOld");
        if (!list.isArray()) {
            return List.of();
        }
        List<PaperStoryFeed.SourceItem> out = new ArrayList<>();
        for (JsonNode n : list) {
            if (out.size() >= limit) {
                break;
            }
            String title = firstText(n, "title");
            if (!StringUtils.hasText(title)) {
                continue;
            }
            String code = firstText(n, "code");
            String url = StringUtils.hasText(code) ? "https://finance.eastmoney.com/a/" + code + ".html" : null;
            out.add(new PaperStoryFeed.SourceItem("MACRO", title, url, firstText(n, "date")));
        }
        return out;
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

    private static String firstText(JsonNode n, String... fields) {
        for (String field : fields) {
            JsonNode v = n.get(field);
            if (v != null && !v.isNull()) {
                String s = v.asText();
                if (StringUtils.hasText(s) && !"-".equals(s)) {
                    return s;
                }
            }
        }
        return null;
    }
}
