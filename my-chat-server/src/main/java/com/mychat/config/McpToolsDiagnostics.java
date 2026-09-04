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
 * 探测失败只打 warn，不中断启动。
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

        // 探测失败不得拖垮启动：远程 MCP（如 Smithery）listTools 500 时仍允许本服务运行
        ToolCallback[] callbacks;
        try {
            callbacks = provider.getToolCallbacks();
        } catch (Exception e) {
            log.warn("MCP listTools 失败，已跳过工具清单探测（聊天仍可用本地 FileTools/searchWeb）：{}",
                    e.getMessage());
            return;
        }

        List<String> names = Arrays.stream(callbacks)
                .map(tc -> tc.getToolDefinition().name())
                .toList();

        if (names.isEmpty()) {
            log.warn("local MCP tools discovered: (none) — 请先启动 mcp-server-demo(8101)，并确认 initialized=true 后重启本服务");
        } else {
            log.info("local MCP tools discovered: {}", names);
        }
    }
}
