package com.mychat.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * 启动时打印 MCP Client 发现到的工具名，便于确认天气等远程工具是否真正生效。
 * 若日志为 (none)，说明未连上 MCP Server，或 Server 未暴露 @McpTool。
 */
@Component
public class McpToolsDiagnostics implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(McpToolsDiagnostics.class);

    private final ObjectProvider<SyncMcpToolCallbackProvider> mcpTools;

    public McpToolsDiagnostics(ObjectProvider<SyncMcpToolCallbackProvider> mcpTools) {
        this.mcpTools = mcpTools;
    }

    @Override
    public void run(ApplicationArguments args) {
        SyncMcpToolCallbackProvider provider = mcpTools.getIfAvailable();
        if (provider == null) {
            log.warn("MCP SyncMcpToolCallbackProvider bean 不存在；检查 spring-ai-starter-mcp-client 依赖与 spring.ai.mcp.client 配置");
            return;
        }

        ToolCallback[] callbacks = provider.getToolCallbacks();
        List<String> names = Arrays.stream(callbacks)
                .map(tc -> tc.getToolDefinition().name())
                .toList();

        if (names.isEmpty()) {
            log.warn("MCP tools discovered: (none) — 请先启动 mcp-server-demo(8101)，并确认 initialized=true 后重启本服务");
        } else {
            log.info("MCP tools discovered: {}", names);
        }
    }
}
