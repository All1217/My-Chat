package com.mychat.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 本地联网搜索（不经 Smithery → mcp.exa.ai）。
 * <p>
 * 优先级：Exa REST → 博查 Bocha → Tavily →（短超时）DuckDuckGo。
 * {@code EXA_API_KEY} 必须是 <a href="https://dashboard.exa.ai/api-keys">Exa Dashboard</a>
 * 签发的密钥，不能填 {@code SMITHERY_API_KEY}。
 */
@Slf4j
@Component
public class WebSearchTools {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(12))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /** DuckDuckGo 在国内常连不上，用更短超时避免拖垮整轮 Agent */
    private final HttpClient shortClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Value("${app.search.exa.api-key:}")
    private String exaApiKey;

    @Value("${app.search.bocha.api-key:}")
    private String bochaApiKey;

    @Value("${app.search.tavily.api-key:}")
    private String tavilyApiKey;

    @Tool(description = """
            联网网页搜索（优先使用本工具，不要用 MCP execute/connections.exa）。
            输入自然语言查询，返回若干条标题+链接+摘要。适用于查资料、案例、新闻、行情摘要等。
            """)
    public String searchWeb(
            @ToolParam(description = "搜索查询词，可用中文或英文") String query) {
        String q = query != null ? query.trim() : "";
        if (!StringUtils.hasText(q)) {
            return "[searchWeb] query 不能为空";
        }

        String exa = trimKey(exaApiKey);
        String bocha = trimKey(bochaApiKey);
        String tavily = trimKey(tavilyApiKey);

        Map<String, String> errors = new LinkedHashMap<>();

        if (StringUtils.hasText(exa)) {
            String r = tryProvider("exa-rest", () -> searchExaRest(q, exa), errors);
            if (r != null) {
                return r;
            }
        } else {
            errors.put("exa-rest", "未配置 EXA_API_KEY（须为 dashboard.exa.ai 密钥，勿填 SMITHERY_API_KEY）");
        }

        if (StringUtils.hasText(bocha)) {
            String r = tryProvider("bocha", () -> searchBocha(q, bocha), errors);
            if (r != null) {
                return r;
            }
        }

        if (StringUtils.hasText(tavily)) {
            String r = tryProvider("tavily", () -> searchTavily(q, tavily), errors);
            if (r != null) {
                return r;
            }
        }

        String r = tryProvider("duckduckgo", () -> searchDuckDuckGoInstant(q), errors);
        if (r != null) {
            return r;
        }

        StringBuilder sb = new StringBuilder("[searchWeb] 全部搜索途径失败。query=").append(q).append('\n');
        errors.forEach((k, v) -> sb.append("- ").append(k).append(": ").append(v).append('\n'));
        sb.append("建议：在 https://dashboard.exa.ai/api-keys 申请真正的 EXA_API_KEY 并注入进程环境变量后重启；")
                .append("或配置 BOCHA_API_KEY / TAVILY_API_KEY。Smithery 的 key 不能用于 api.exa.ai。");
        return sb.toString().trim();
    }

    private String tryProvider(String name, SearchCall call, Map<String, String> errors) {
        try {
            String out = call.run();
            if (out != null && !out.startsWith("[searchWeb]")) {
                return out;
            }
            errors.put(name, out != null ? truncate(out, 220) : "empty");
            log.warn("searchWeb provider={} 未成功: {}", name, truncate(out, 160));
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            errors.put(name, msg);
            log.warn("searchWeb provider={} 异常: {}", name, msg);
        }
        return null;
    }

    @FunctionalInterface
    private interface SearchCall {
        String run() throws Exception;
    }

    private String searchExaRest(String query, String apiKey) throws Exception {
        ObjectNode contents = MAPPER.createObjectNode();
        contents.put("text", true);
        contents.put("maxCharacters", 400);

        ObjectNode payload = MAPPER.createObjectNode();
        payload.put("query", query);
        payload.put("type", "auto");
        payload.put("numResults", 5);
        payload.set("contents", contents);
        String body = MAPPER.writeValueAsString(payload);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.exa.ai/search"))
                .timeout(Duration.ofSeconds(45))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 401 || response.statusCode() == 403) {
            return "[searchWeb] Exa REST HTTP " + response.statusCode()
                    + " Invalid/forbidden API key。请确认 EXA_API_KEY 来自 https://dashboard.exa.ai/api-keys ，"
                    + "不是 SMITHERY_API_KEY。body=" + truncate(response.body(), 180);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return "[searchWeb] Exa REST HTTP " + response.statusCode() + ": "
                    + truncate(response.body(), 300);
        }

        JsonNode root = MAPPER.readTree(response.body());
        JsonNode results = root.path("results");
        if (!results.isArray() || results.isEmpty()) {
            return "[searchWeb] Exa REST 无结果。query=" + query;
        }

        StringBuilder sb = new StringBuilder("联网搜索结果（Exa REST）query=").append(query).append('\n');
        int i = 1;
        for (JsonNode item : results) {
            if (i > 5) {
                break;
            }
            sb.append(i++).append(". ")
                    .append(text(item, "title")).append('\n')
                    .append("   URL: ").append(text(item, "url")).append('\n');
            String snippet = text(item.path("text"));
            if (!snippet.isBlank()) {
                sb.append("   ").append(truncate(snippet, 280)).append('\n');
            }
        }
        return sb.toString().trim();
    }

    private String searchBocha(String query, String apiKey) throws Exception {
        ObjectNode payload = MAPPER.createObjectNode();
        payload.put("query", query);
        payload.put("count", 5);
        payload.put("summary", true);
        String body = MAPPER.writeValueAsString(payload);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.bochaai.com/v1/web-search"))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return "[searchWeb] Bocha HTTP " + response.statusCode() + ": "
                    + truncate(response.body(), 300);
        }

        JsonNode root = MAPPER.readTree(response.body());
        JsonNode webPages = root.path("data").path("webPages").path("value");
        if (!webPages.isArray() || webPages.isEmpty()) {
            webPages = root.path("data").path("webPages");
        }
        if (!webPages.isArray() || webPages.isEmpty()) {
            return "[searchWeb] Bocha 无结果。query=" + query + " body=" + truncate(response.body(), 200);
        }

        StringBuilder sb = new StringBuilder("联网搜索结果（Bocha）query=").append(query).append('\n');
        int i = 1;
        for (JsonNode item : webPages) {
            if (i > 5) {
                break;
            }
            String title = firstNonBlank(text(item, "name"), text(item, "title"));
            String url = firstNonBlank(text(item, "url"), text(item, "displayUrl"));
            String snippet = firstNonBlank(text(item, "snippet"), text(item, "summary"));
            sb.append(i++).append(". ").append(title).append('\n');
            if (!url.isBlank()) {
                sb.append("   URL: ").append(url).append('\n');
            }
            if (!snippet.isBlank()) {
                sb.append("   ").append(truncate(snippet, 280)).append('\n');
            }
        }
        return sb.toString().trim();
    }

    private String searchTavily(String query, String apiKey) throws Exception {
        ObjectNode payload = MAPPER.createObjectNode();
        payload.put("api_key", apiKey);
        payload.put("query", query);
        payload.put("search_depth", "basic");
        payload.put("max_results", 5);
        String body = MAPPER.writeValueAsString(payload);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.tavily.com/search"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return "[searchWeb] Tavily HTTP " + response.statusCode() + ": "
                    + truncate(response.body(), 300);
        }

        JsonNode results = MAPPER.readTree(response.body()).path("results");
        if (!results.isArray() || results.isEmpty()) {
            return "[searchWeb] Tavily 无结果。query=" + query;
        }

        StringBuilder sb = new StringBuilder("联网搜索结果（Tavily）query=").append(query).append('\n');
        int i = 1;
        for (JsonNode item : results) {
            if (i > 5) {
                break;
            }
            sb.append(i++).append(". ").append(text(item, "title")).append('\n')
                    .append("   URL: ").append(text(item, "url")).append('\n');
            String snippet = text(item, "content");
            if (!snippet.isBlank()) {
                sb.append("   ").append(truncate(snippet, 280)).append('\n');
            }
        }
        return sb.toString().trim();
    }

    private String searchDuckDuckGoInstant(String query) throws Exception {
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.duckduckgo.com/?q=" + encoded
                        + "&format=json&no_html=1&skip_disambig=1"))
                .timeout(Duration.ofSeconds(8))
                .header("User-Agent", "MyChatSearchBot/1.0")
                .GET()
                .build();
        HttpResponse<String> response = shortClient.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode root = MAPPER.readTree(response.body());
        String abstractText = text(root, "AbstractText");
        if (StringUtils.hasText(abstractText)) {
            return "联网搜索结果（DuckDuckGo Instant）query=" + query + "\n摘要: "
                    + abstractText + "\nURL: " + text(root, "AbstractURL");
        }
        JsonNode topics = root.path("RelatedTopics");
        List<String> lines = new ArrayList<>();
        if (topics.isArray()) {
            for (JsonNode t : topics) {
                if (lines.size() >= 5) {
                    break;
                }
                String text = text(t, "Text");
                String url = text(t.path("FirstURL"));
                if (!text.isBlank()) {
                    lines.add((lines.size() + 1) + ". " + text
                            + (url.isBlank() ? "" : "\n   URL: " + url));
                }
            }
        }
        if (lines.isEmpty()) {
            return "[searchWeb] DuckDuckGo 无结果或不可达（国内常超时）";
        }
        return "联网搜索结果（DuckDuckGo Instant）query=" + query + "\n" + String.join("\n", lines);
    }

    private static String trimKey(String key) {
        return key == null ? "" : key.trim();
    }

    private static String firstNonBlank(String a, String b) {
        return StringUtils.hasText(a) ? a : (b != null ? b : "");
    }

    private static String text(JsonNode node, String field) {
        return text(node.path(field));
    }

    private static String text(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? "" : node.asText("");
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.replace('\n', ' ').trim();
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }
}
