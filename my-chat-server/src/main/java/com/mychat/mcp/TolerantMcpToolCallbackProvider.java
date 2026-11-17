package com.mychat.mcp;

import com.mychat.mcp.settings.McpRuntimeSettingsService;
import com.mychat.mcp.settings.entity.po.McpConnectionSettings;
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
import java.util.Locale;

/**
 * 按 MCP 连接分别 listTools：某一端 500 不影响其余连接已发现的工具。
 * 每次发现时再按运行时 DB 开关过滤，改开关无需重启。
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
    private final McpRuntimeSettingsService runtimeSettings;

    /**
     * 生产构造：每个 YAML connection 对应一个 {@link McpSyncClient}。
     */
    public TolerantMcpToolCallbackProvider(List<McpSyncClient> clients,
                                           McpToolNamePrefixGenerator prefixGenerator) {
        this(clients, prefixGenerator, null);
    }

    /** 带运行时开关的生产构造。 */
    public TolerantMcpToolCallbackProvider(List<McpSyncClient> clients,
                                           McpToolNamePrefixGenerator prefixGenerator,
                                           McpRuntimeSettingsService runtimeSettings) {
        McpToolNamePrefixGenerator prefix = prefixGenerator != null
                ? prefixGenerator
                : new DefaultMcpToolNamePrefixGenerator();
        this.clients = copyClients(clients);
        this.lister = client -> SyncMcpToolCallbackProvider.builder()
                .mcpClients(client)
                .toolNamePrefixGenerator(prefix)
                .build()
                .getToolCallbacks();
        this.runtimeSettings = runtimeSettings;
    }

    /**
     * 测试构造：用假 listTools 验证「一连接失败不影响其余」。
     */
    TolerantMcpToolCallbackProvider(List<McpSyncClient> clients, ClientToolLister lister) {
        this.clients = copyClients(clients);
        this.lister = lister != null ? lister : client -> new ToolCallback[0];
        this.runtimeSettings = null;
    }

    /**
     * 合并各连接工具；单个 listTools 异常只跳过该连接。
     */
    @Override
    public ToolCallback[] getToolCallbacks() {
        List<ToolCallback> all = new ArrayList<>();
        for (McpSyncClient client : clients) {
            if (!runtimeEnabled(client)) {
                log.debug("MCP [{}] 运行时已关闭，跳过 listTools", describe(client));
                continue;
            }
            try {
                ToolCallback[] cbs = listOne(client);
                if (cbs != null && cbs.length > 0) {
                    Collections.addAll(all, cbs);
                }
            } catch (Exception e) {
                log.warn("MCP [{}] listTools 失败，已跳过该连接: {}", describe(client), e.getMessage());
                // Smithery 已 initialize 成功但 listTools 500：仍挂命名空间搜索工具，走 callTool
                if (isSmithery(client) && runtimeEnabled(client)) {
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

    /** 运行时 DB 开关；无服务则视为开启。 */
    private boolean runtimeEnabled(McpSyncClient client) {
        if (runtimeSettings == null) {
            return true;
        }
        String d = describe(client);
        if (isDatabase(client) || (d != null && d.toLowerCase(Locale.ROOT).contains("database"))) {
            return runtimeSettings.isEnabled(McpConnectionSettings.ID_DATABASE);
        }
        if (d != null && d.toLowerCase(Locale.ROOT).contains("crawler")) {
            return runtimeSettings.isEnabled(McpConnectionSettings.ID_CRAWLER);
        }
        if (isSmithery(client)) {
            return runtimeSettings.isEnabled(McpConnectionSettings.ID_SMITHERY);
        }
        return true;
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
    public static String describe(McpSyncClient client) {
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

    /** 是否为本地数据库 MCP 连接。 */
    static boolean isDatabase(McpSyncClient client) {
        String d = describe(client);
        if (d.contains("database") || d.contains("mcp-database")) {
            return true;
        }
        try {
            var server = client.getServerInfo();
            return server != null && hasText(server.name())
                    && (server.name().contains("database") || server.name().contains("mcp-database"));
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
