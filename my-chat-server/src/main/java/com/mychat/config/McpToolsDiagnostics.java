package com.mychat.config;

import io.modelcontextprotocol.client.McpSyncClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.McpToolNamePrefixGenerator;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.Arrays;
import java.util.List;

/**
 * 启动时按连接打印 MCP 工具名：某一端 listTools 失败不影响其它连接的探测。
 */
@Component
public class McpToolsDiagnostics implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(McpToolsDiagnostics.class);

    private final ObjectProvider<List<McpSyncClient>> mcpSyncClients;
    private final ObjectProvider<McpToolNamePrefixGenerator> prefixGenerator;

    public McpToolsDiagnostics(ObjectProvider<List<McpSyncClient>> mcpSyncClients,
                               ObjectProvider<McpToolNamePrefixGenerator> prefixGenerator) {
        this.mcpSyncClients = mcpSyncClients;
        this.prefixGenerator = prefixGenerator;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<McpSyncClient> clients = mcpSyncClients.getIfAvailable();
        if (CollectionUtils.isEmpty(clients)) {
            log.warn("未发现 McpSyncClient；检查 spring-ai-starter-mcp-client 与 spring.ai.mcp.client 配置");
            return;
        }

        // 与 ChatClient 同一套按连接隔离逻辑，日志能看出哪条 connection 失败
        var provider = new TolerantMcpToolCallbackProvider(clients, prefixGenerator.getIfAvailable());
        int ok = 0;
        for (McpSyncClient client : clients) {
            String name = TolerantMcpToolCallbackProvider.describe(client);
            ToolCallback[] callbacks;
            try {
                callbacks = provider.listOne(client);
            } catch (Exception e) {
                log.warn("MCP [{}] listTools 失败: {}", name, e.getMessage());
                continue;
            }
            if (callbacks == null) {
                callbacks = new ToolCallback[0];
            }
            List<String> toolNames = Arrays.stream(callbacks)
                    .map(tc -> tc.getToolDefinition().name())
                    .toList();
            if (toolNames.isEmpty()) {
                log.warn("MCP [{}] tools: (none)", name);
            } else {
                ok++;
                log.info("MCP [{}] tools: {}", name, toolNames);
            }
        }
        if (ok == 0) {
            log.warn("全部 MCP 连接均未发现工具 — 请启动 mcp-server-demo(8101)，并确认 SMITHERY_API_KEY / initialized=true");
        }
    }
}
