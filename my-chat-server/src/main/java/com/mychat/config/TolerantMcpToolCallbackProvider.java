package com.mychat.config;

import io.modelcontextprotocol.client.McpSyncClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.DefaultMcpToolNamePrefixGenerator;
import org.springframework.ai.mcp.McpToolNamePrefixGenerator;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 按 MCP 连接分别 listTools：某一端 500 不影响其余连接已发现的工具。
 */
public final class TolerantMcpToolCallbackProvider implements ToolCallbackProvider {

    private static final Logger log = LoggerFactory.getLogger(TolerantMcpToolCallbackProvider.class);

    /** 列出单个连接的工具；测试可注入假实现。 */
    @FunctionalInterface
    interface ClientToolLister {
        ToolCallback[] list(McpSyncClient client);
    }

    private final List<McpSyncClient> clients;
    private final ClientToolLister lister;

    /**
     * 生产构造：每个 YAML connection 对应一个 {@link McpSyncClient}。
     */
    public TolerantMcpToolCallbackProvider(List<McpSyncClient> clients,
                                           McpToolNamePrefixGenerator prefixGenerator) {
        McpToolNamePrefixGenerator prefix = prefixGenerator != null
                ? prefixGenerator
                : new DefaultMcpToolNamePrefixGenerator();
        this.clients = copyClients(clients);
        this.lister = client -> SyncMcpToolCallbackProvider.builder()
                .mcpClients(client)
                .toolNamePrefixGenerator(prefix)
                .build()
                .getToolCallbacks();
    }

    /**
     * 测试构造：用假 listTools 验证「一连接失败不影响其余」。
     */
    TolerantMcpToolCallbackProvider(List<McpSyncClient> clients, ClientToolLister lister) {
        this.clients = copyClients(clients);
        this.lister = lister != null ? lister : client -> new ToolCallback[0];
    }

    /**
     * 合并各连接工具；单个 listTools 异常只跳过该连接。
     */
    @Override
    public ToolCallback[] getToolCallbacks() {
        List<ToolCallback> all = new ArrayList<>();
        for (McpSyncClient client : clients) {
            try {
                ToolCallback[] cbs = listOne(client);
                if (cbs != null && cbs.length > 0) {
                    Collections.addAll(all, cbs);
                }
            } catch (Exception e) {
                log.warn("MCP [{}] listTools 失败，已跳过该连接: {}", describe(client), e.getMessage());
                // Smithery 已 initialize 成功但 listTools 500：仍挂命名空间搜索工具，走 callTool
                if (isSmithery(client)) {
                    ToolCallback fallback = new FallbackSmitheryToolCallback(
                            client,
                            "exa.search",
                            "Smithery 命名空间网页搜索（Exa）。参数 query 为搜索词。listTools 失败时的兜底挂载。");
                    all.add(fallback);
                    log.warn("MCP [{}] 已挂载兜底工具 {}", describe(client),
                            fallback.getToolDefinition().name());
                }
            }
        }
        return all.toArray(ToolCallback[]::new);
    }

    /**
     * 列出单个连接的工具；失败抛给调用方（启动诊断用）。
     */
    ToolCallback[] listOne(McpSyncClient client) {
        return lister.list(client);
    }

    /**
     * 连接展示名：优先 clientInfo.title（YAML connection 名），否则 name / toString。
     */
    static String describe(McpSyncClient client) {
        if (client == null) {
            return "unknown";
        }
        try {
            var info = client.getClientInfo();
            if (info != null) {
                if (hasText(info.title())) {
                    return info.title();
                }
                if (hasText(info.name())) {
                    return info.name();
                }
            }
        } catch (Exception ignored) {
            // 未 initialize 时可能读不到 clientInfo
        }
        try {
            var server = client.getServerInfo();
            if (server != null && hasText(server.name())) {
                return server.name();
            }
        } catch (Exception ignored) {
            // 同上
        }
        return String.valueOf(client);
    }

    /**
     * 是否为 YAML 里的 smithery-toolbox / 命名空间端点。
     */
    static boolean isSmithery(McpSyncClient client) {
        String d = describe(client);
        if (d.contains("smithery") || d.contains("2625705206")) {
            return true;
        }
        try {
            var server = client.getServerInfo();
            return server != null && hasText(server.name())
                    && (server.name().contains("smithery") || server.name().contains("2625705206"));
        } catch (Exception ignored) {
            return false;
        }
    }

    private static List<McpSyncClient> copyClients(List<McpSyncClient> clients) {
        return CollectionUtils.isEmpty(clients) ? List.of() : List.copyOf(clients);
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
