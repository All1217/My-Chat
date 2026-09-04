package com.mychat.config;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.McpHttpClientTransportAuthorizationException;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Smithery listTools 500 时仍把已知搜索工具挂上，走同一条 MCP callTool。
 */
final class FallbackSmitheryToolCallback implements ToolCallback {

    private static final Logger log = LoggerFactory.getLogger(FallbackSmitheryToolCallback.class);

    private static final String INPUT_SCHEMA = """
            {"type":"object","properties":{"query":{"type":"string","description":"搜索查询词"}},"required":["query"]}
            """;

    private final McpSyncClient client;
    private final String toolName;
    private final ToolDefinition definition;

    FallbackSmitheryToolCallback(McpSyncClient client, String toolName, String description) {
        this.client = client;
        this.toolName = toolName;
        this.definition = ToolDefinition.builder()
                .name(toolName.replace('.', '_'))
                .description(description)
                .inputSchema(INPUT_SCHEMA)
                .build();
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return definition;
    }

    @Override
    public String call(String toolInput) {
        Map<String, Object> args = parseArgs(toolInput);
        try {
            McpSchema.CallToolResult result = client.callTool(
                    new McpSchema.CallToolRequest(toolName, args));
            String text = String.valueOf(result);
            log.info("Smithery fallback callTool name={} isError={}",
                    toolName, result.isError());
            if (Boolean.TRUE.equals(result.isError())) {
                return "[MCP error] " + text;
            }
            return text;
        } catch (McpHttpClientTransportAuthorizationException e) {
            int status = e.getResponseInfo() == null ? -1 : e.getResponseInfo().statusCode();
            log.warn("Smithery fallback callTool 被上游拒绝 name={} HTTP {}", toolName, status);
            return "[MCP HTTP " + status + "] 上游拒绝 tools/call（Cloudflare/WAF HTML，不是 JSON-RPC 鉴权失败）。";
        } catch (Exception e) {
            log.warn("Smithery fallback callTool 失败 name={}: {}", toolName, e.getMessage());
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseArgs(String toolInput) {
        if (toolInput == null || toolInput.isBlank() || "{}".equals(toolInput.trim())) {
            return Map.of();
        }
        try {
            Object parsed = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(toolInput, Object.class);
            if (parsed instanceof Map<?, ?> map) {
                Map<String, Object> out = new LinkedHashMap<>();
                map.forEach((k, v) -> out.put(String.valueOf(k), v));
                return out;
            }
        } catch (Exception e) {
            log.warn("fallback 工具参数解析失败，按 query 原文传递: {}", e.getMessage());
        }
        return Map.of("query", toolInput);
    }
}
