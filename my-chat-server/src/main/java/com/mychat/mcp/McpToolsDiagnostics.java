package com.mychat.mcp;

import io.modelcontextprotocol.client.McpSyncClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.McpToolNamePrefixGenerator;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.util.CollectionUtils;

import java.util.Arrays;
import java.util.List;

/**
 * 启动时按连接打印 MCP 工具名：某一端 listTools 失败不影响其它连接，也不阻止启动。
 */
public class McpToolsDiagnostics implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(McpToolsDiagnostics.class);

    private final McpProperties properties;
    private final List<McpSyncClient> clients;
    private final McpToolNamePrefixGenerator prefixGenerator;

    /** 注入属性与客户端列表（可为空）。 */
    public McpToolsDiagnostics(McpProperties properties,
                               List<McpSyncClient> clients,
                               McpToolNamePrefixGenerator prefixGenerator) {
        this.properties = properties;
        this.clients = clients == null ? List.of() : List.copyOf(clients);
        this.prefixGenerator = prefixGenerator;
    }

    /**
     * 过滤后逐连接 listTools；全部失败只 warn。
     */
    @Override
    public void run(ApplicationArguments args) {
        try {
            runDiagnostics();
        } catch (Exception e) {
            // 诊断绝不能拖垮启动
            log.warn("MCP 启动诊断异常（已忽略，不影响主应用）: {}", e.getMessage());
        }
    }

    /** 实际诊断逻辑，供测试直接调用。 */
    void runDiagnostics() {
        DefaultMcpToolBridge bridge = new DefaultMcpToolBridge(properties, clients, prefixGenerator);
        List<McpSyncClient> allowed = bridge.filterClients(clients);
        if (CollectionUtils.isEmpty(allowed)) {
            log.warn("未发现可用 McpSyncClient；检查 app.mcp.* 开关、spring.ai.mcp.client 与远端服务");
            return;
        }

        var provider = new TolerantMcpToolCallbackProvider(allowed, prefixGenerator);
        int ok = 0;
        for (McpSyncClient client : allowed) {
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
            log.warn("全部 MCP 连接均未发现工具 — 请启动 mcp-server-database(8102)，并确认 SMITHERY_API_KEY；"
                    + "单连接失败不应阻止启动；也可设 APP_MCP_ENABLED=false 关闭本模块");
        }
    }
}
