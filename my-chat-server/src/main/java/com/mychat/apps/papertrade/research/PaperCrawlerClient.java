package com.mychat.apps.papertrade.research;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mychat.apps.papertrade.research.entity.vo.PaperCrawlerProbeVO;
import com.mychat.mcp.TolerantMcpToolCallbackProvider;
import com.mychat.mcp.settings.McpRuntimeSettingsService;
import com.mychat.mcp.settings.entity.po.McpConnectionSettings;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 调用本地 mcp-crawler 的 fetch_url；失败返回 empty，不抛到业务整轮。
 */
@Slf4j
@Component
public class PaperCrawlerClient {

    private static final int TEXT_CLIP = 800;
    private static final int DETAIL_CLIP = 240;

    private final ObjectProvider<List<McpSyncClient>> clientsProvider;
    private final McpRuntimeSettingsService runtimeSettings;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PaperCrawlerClient(ObjectProvider<List<McpSyncClient>> clientsProvider,
                              McpRuntimeSettingsService runtimeSettings) {
        this.clientsProvider = clientsProvider;
        this.runtimeSettings = runtimeSettings;
    }

    /**
     * 探测本地爬虫 MCP 连通性：先看运行时开关，再调 list_adapters（不抓外网）。
     */
    public PaperCrawlerProbeVO probe() {
        // 1) 运行时开关
        if (!runtimeSettings.isEnabled(McpConnectionSettings.ID_CRAWLER)) {
            return PaperCrawlerProbeVO.of(
                    false,
                    "DISABLED",
                    "本地网页抓取 MCP（mcp-crawler-server）未启用，请先到「设置 → MCP 工具管理」开启后再测。",
                    null);
        }
        // 2) 客户端是否挂上
        McpSyncClient client = findCrawler();
        if (client == null) {
            return PaperCrawlerProbeVO.of(
                    false,
                    "NO_CLIENT",
                    "已启用但未连上爬虫客户端，请确认本机 mcp-server-crawler 已启动（默认 http://localhost:8103/mcp），并检查 APP_MCP_* / MCP_CRAWLER_URL。",
                    null);
        }
        // 3) 轻量工具调用
        try {
            McpSchema.CallToolResult result = client.callTool(
                    new McpSchema.CallToolRequest("list_adapters", Map.of()));
            if (Boolean.TRUE.equals(result.isError())) {
                return PaperCrawlerProbeVO.of(
                        false,
                        "ERROR",
                        "爬虫 MCP 已连上，但 list_adapters 返回错误。",
                        clip(String.valueOf(result), DETAIL_CLIP));
            }
            String detail = clip(String.valueOf(result), DETAIL_CLIP);
            String name = TolerantMcpToolCallbackProvider.describe(client);
            return PaperCrawlerProbeVO.of(
                    true,
                    "OK",
                    "本地爬虫 MCP 连通正常" + (StringUtils.hasText(name) ? "（" + name + "）" : "") + "。",
                    detail);
        } catch (Exception e) {
            log.warn("crawler probe 失败: {}", e.getMessage());
            return PaperCrawlerProbeVO.of(
                    false,
                    "ERROR",
                    "调用爬虫 MCP 失败：" + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()),
                    null);
        }
    }

    /** 抓取页面正文摘要；不可用或失败时 empty。 */
    public Optional<String> fetchText(String url) {
        if (!StringUtils.hasText(url)) {
            return Optional.empty();
        }
        if (!runtimeSettings.isEnabled(McpConnectionSettings.ID_CRAWLER)) {
            return Optional.empty();
        }
        McpSyncClient client = findCrawler();
        if (client == null) {
            return Optional.empty();
        }
        try {
            Map<String, Object> args = new LinkedHashMap<>();
            args.put("url", url.trim());
            McpSchema.CallToolResult result = client.callTool(
                    new McpSchema.CallToolRequest("fetch_url", args));
            if (Boolean.TRUE.equals(result.isError())) {
                log.debug("crawler fetch_url error: {}", result);
                return Optional.empty();
            }
            // CallToolResult 文本形态因 SDK 版本而异，统一 toString 再抽 text
            String raw = String.valueOf(result);
            if (!StringUtils.hasText(raw)) {
                return Optional.empty();
            }
            String body = parseBodyText(raw);
            if (!StringUtils.hasText(body)) {
                return Optional.empty();
            }
            return Optional.of(clip(body, TEXT_CLIP));
        } catch (Exception e) {
            log.debug("crawler fetch_url 跳过 {}: {}", url, e.getMessage());
            return Optional.empty();
        }
    }

    private McpSyncClient findCrawler() {
        List<McpSyncClient> clients = clientsProvider.getIfAvailable();
        if (CollectionUtils.isEmpty(clients)) {
            return null;
        }
        for (McpSyncClient c : clients) {
            String d = TolerantMcpToolCallbackProvider.describe(c);
            if (d != null && d.toLowerCase().contains("crawler")) {
                return c;
            }
        }
        return null;
    }

    private String parseBodyText(String raw) {
        String trimmed = raw.trim();
        if (trimmed.startsWith("{")) {
            try {
                JsonNode n = objectMapper.readTree(trimmed);
                if (n.hasNonNull("text")) {
                    return n.get("text").asText();
                }
                if (n.path("ok").asBoolean(true) == false) {
                    return null;
                }
            } catch (Exception ignored) {
                // 非 JSON 则整段当正文
            }
        }
        return trimmed;
    }

    private static String clip(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
