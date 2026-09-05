package com.mychat.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mychat.config.SearchProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 本地联网搜索：直连 Exa REST，失败再试 Bocha / Tavily。
 * 不经 {@code mcp.smithery.run} / {@code mcp.exa.ai}，避开 Cloudflare 403。
 */
@Component
public class WebSearchTools {

    private static final Logger log = LoggerFactory.getLogger(WebSearchTools.class);

    private static final int NUM_RESULTS = 8;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(20);

    private final SearchProperties properties;
    private final RestClient restClient;
    private final ObjectMapper mapper;

    /**
     * 生产构造：自建带超时的 RestClient，避免改动容器里共享的 Builder。
     * {@code @Autowired} 指定给 Spring 用这一条；否则双构造器会退化成无参实例化。
     */
    @Autowired
    public WebSearchTools(SearchProperties properties) {
        this(properties, RestClient.builder().requestFactory(jdkFactory()).build(), new ObjectMapper());
    }

    /**
     * 测试构造：注入已绑定 Mock 的 RestClient。
     */
    WebSearchTools(SearchProperties properties, RestClient restClient, ObjectMapper mapper) {
        this.properties = properties;
        this.restClient = restClient;
        this.mapper = mapper;
    }

    /**
     * 给模型调用的网页搜索入口；按 Exa → Bocha → Tavily 回退，失败返回可读错误。
     */
    @Tool(description = "网页搜索。传入 query，返回带标题、URL 与摘要的检索结果。优先使用本工具，不要走 Smithery/MCP 搜索。")
    public String searchWeb(
            @ToolParam(description = "搜索查询词，如新闻事件、城市名单、产品对比") String query) {
        // 1. 校验查询词
        if (!StringUtils.hasText(query)) {
            return "错误: query 为空，无法搜索";
        }
        String q = query.trim();

        // 2. 按顺序尝试已配置的供应商
        List<String> errors = new ArrayList<>();
        if (hasKey(properties.getExa())) {
            String result = searchExa(q);
            if (isOk(result)) {
                return result;
            }
            errors.add(result);
        }
        if (hasKey(properties.getBocha())) {
            String result = searchBocha(q);
            if (isOk(result)) {
                return result;
            }
            errors.add(result);
        }
        if (hasKey(properties.getTavily())) {
            String result = searchTavily(q);
            if (isOk(result)) {
                return result;
            }
            errors.add(result);
        }

        // 3. 全部不可用：区分「没配密钥」与「都请求失败」
        if (errors.isEmpty()) {
            return "未配置搜索密钥。请设置 EXA_API_KEY（来自 https://dashboard.exa.ai/api-keys，不要填 SMITHERY_API_KEY），或可选 BOCHA_API_KEY / TAVILY_API_KEY。";
        }
        return String.join("\n---\n", errors);
    }

    /**
     * 组装 Exa /search 请求体；用 set 挂嵌套节点，避免 Jackson put(JsonNode) NPE。
     */
    String buildExaRequestBody(String query) {
        ObjectNode root = mapper.createObjectNode();
        root.put("query", query);
        root.put("type", "auto");
        root.put("numResults", NUM_RESULTS);
        ObjectNode contents = mapper.createObjectNode();
        contents.put("highlights", true);
        root.set("contents", contents);
        try {
            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("组装 Exa 请求体失败", e);
        }
    }

    /**
     * 调用 Exa POST /search 并格式化结果。
     */
    private String searchExa(String query) {
        SearchProperties.Provider exa = properties.getExa();
        String url = joinUrl(exa.getBaseUrl(), "/search");
        String body = buildExaRequestBody(query);
        HttpExchange exchange = postJson(url, "Bearer " + exa.getApiKey(), body);
        if (exchange.status() == 200) {
            return formatExa(exchange.body());
        }
        log.warn("exa-rest 失败 status={} body={}", exchange.status(), clip(exchange.body()));
        return "provider=exa-rest status=" + exchange.status() + " " + clip(exchange.body());
    }

    /**
     * 调用博查 Web Search 并格式化结果。
     */
    private String searchBocha(String query) {
        SearchProperties.Provider bocha = properties.getBocha();
        String url = joinUrl(bocha.getBaseUrl(), "/v1/web-search");
        ObjectNode root = mapper.createObjectNode();
        root.put("query", query);
        root.put("count", NUM_RESULTS);
        root.put("summary", true);
        HttpExchange exchange = postJson(url, "Bearer " + bocha.getApiKey(), write(root));
        if (exchange.status() == 200) {
            return formatBocha(exchange.body());
        }
        log.warn("bocha-rest 失败 status={} body={}", exchange.status(), clip(exchange.body()));
        return "provider=bocha-rest status=" + exchange.status() + " " + clip(exchange.body());
    }

    /**
     * 调用 Tavily /search 并格式化结果。
     */
    private String searchTavily(String query) {
        SearchProperties.Provider tavily = properties.getTavily();
        String url = joinUrl(tavily.getBaseUrl(), "/search");
        ObjectNode root = mapper.createObjectNode();
        root.put("api_key", tavily.getApiKey());
        root.put("query", query);
        root.put("max_results", NUM_RESULTS);
        HttpExchange exchange = postJson(url, null, write(root));
        if (exchange.status() == 200) {
            return formatTavily(exchange.body());
        }
        log.warn("tavily-rest 失败 status={} body={}", exchange.status(), clip(exchange.body()));
        return "provider=tavily-rest status=" + exchange.status() + " " + clip(exchange.body());
    }

    /**
     * POST JSON；不抛 HTTP 状态异常，便于 401 后回退下一供应商。
     */
    private HttpExchange postJson(String url, String authorization, String jsonBody) {
        try {
            return restClient.post()
                    .uri(URI.create(url))
                    .headers(h -> {
                        h.setContentType(MediaType.APPLICATION_JSON);
                        if (StringUtils.hasText(authorization)) {
                            h.set("Authorization", authorization);
                        }
                    })
                    .body(jsonBody)
                    .exchange((request, response) -> {
                        String body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
                        return new HttpExchange(response.getStatusCode().value(), body);
                    });
        } catch (Exception e) {
            log.warn("搜索 HTTP 请求失败 url={}: {}", url, e.getMessage());
            return new HttpExchange(-1, e.getMessage() == null ? "request failed" : e.getMessage());
        }
    }

    /**
     * 把 Exa results 压成 title / url / highlight 短文本。
     */
    private String formatExa(String json) {
        JsonNode root = readTree(json);
        JsonNode results = root.path("results");
        StringBuilder sb = new StringBuilder("provider=exa-rest status=200\n");
        appendItems(sb, results, "title", "url", "highlights");
        return sb.toString();
    }

    /**
     * 把博查 webPages.value 压成短文本。
     */
    private String formatBocha(String json) {
        JsonNode pages = readTree(json).path("data").path("webPages").path("value");
        StringBuilder sb = new StringBuilder("provider=bocha-rest status=200\n");
        appendItems(sb, pages, "name", "url", "summary");
        return sb.toString();
    }

    /**
     * 把 Tavily results 压成短文本。
     */
    private String formatTavily(String json) {
        JsonNode results = readTree(json).path("results");
        StringBuilder sb = new StringBuilder("provider=tavily-rest status=200\n");
        appendItems(sb, results, "title", "url", "content");
        return sb.toString();
    }

    /**
     * 按统一编号输出条目；snippet 字段若是数组则取第一段。
     */
    private void appendItems(StringBuilder sb, JsonNode items, String titleField, String urlField, String snippetField) {
        if (items == null || !items.isArray() || items.isEmpty()) {
            sb.append("（无结果）\n");
            return;
        }
        int i = 1;
        for (JsonNode item : items) {
            sb.append(i++).append(". ").append(text(item, titleField)).append('\n');
            sb.append("   ").append(text(item, urlField)).append('\n');
            JsonNode snippet = item.get(snippetField);
            String excerpt = excerpt(snippet);
            if (StringUtils.hasText(excerpt)) {
                sb.append("   ").append(excerpt).append('\n');
            }
        }
    }

    /**
     * 从字符串或字符串数组取出一段摘要。
     */
    private static String excerpt(JsonNode snippet) {
        if (snippet == null || snippet.isNull()) {
            return "";
        }
        if (snippet.isArray()) {
            return snippet.isEmpty() ? "" : snippet.get(0).asText("");
        }
        return snippet.asText("");
    }

    /**
     * 读取对象字段文本，缺省为空串。
     */
    private static String text(JsonNode node, String field) {
        return node == null ? "" : node.path(field).asText("");
    }

    /**
     * 解析 JSON；非法内容当成空对象，避免格式化阶段抛错。
     */
    private JsonNode readTree(String json) {
        try {
            return mapper.readTree(json == null ? "{}" : json);
        } catch (Exception e) {
            return mapper.createObjectNode();
        }
    }

    /**
     * 把 ObjectNode 写成 JSON 字符串。
     */
    private String write(ObjectNode node) {
        try {
            return mapper.writeValueAsString(node);
        } catch (Exception e) {
            throw new IllegalStateException("组装搜索请求体失败", e);
        }
    }

    /**
     * 200 且带 provider 前缀视为成功（含「无结果」）。
     */
    private static boolean isOk(String result) {
        return result != null && result.contains("status=200");
    }

    /**
     * 供应商是否已配置非空密钥。
     */
    private static boolean hasKey(SearchProperties.Provider provider) {
        return provider != null && StringUtils.hasText(provider.getApiKey());
    }

    /**
     * 拼接 baseUrl 与 path，避免双斜杠。
     */
    private static String joinUrl(String baseUrl, String path) {
        String base = baseUrl == null ? "" : baseUrl.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + path;
    }

    /**
     * 截断上游错误体，避免把整页 HTML 灌进模型。
     */
    private static String clip(String body) {
        if (body == null) {
            return "";
        }
        String t = body.replace('\n', ' ').trim();
        return t.length() <= 300 ? t : t.substring(0, 300) + "…";
    }

    /**
     * 构造带连接/读取超时的 JDK 请求工厂。
     */
    private static JdkClientHttpRequestFactory jdkFactory() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(READ_TIMEOUT);
        return factory;
    }

    /**
     * 一次 HTTP 交换的状态码与响应体。
     */
    record HttpExchange(int status, String body) {
    }
}
