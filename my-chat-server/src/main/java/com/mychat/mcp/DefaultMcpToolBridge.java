package com.mychat.mcp;

import io.modelcontextprotocol.client.McpSyncClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.mcp.McpToolNamePrefixGenerator;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 按 app.mcp 子开关过滤连接后，把 Tolerant MCP 工具挂到 ChatClient。
 */
public final class DefaultMcpToolBridge implements McpToolBridge {

    private static final Logger log = LoggerFactory.getLogger(DefaultMcpToolBridge.class);

    private final McpProperties properties;
    private final List<McpSyncClient> clients;
    private final McpToolNamePrefixGenerator prefixGenerator;

    /**
     * 注入属性与已创建的 MCP 客户端；clients 可为空。
     */
    public DefaultMcpToolBridge(McpProperties properties,
                                List<McpSyncClient> clients,
                                McpToolNamePrefixGenerator prefixGenerator) {
        this.properties = properties;
        this.clients = clients == null ? List.of() : List.copyOf(clients);
        this.prefixGenerator = prefixGenerator;
    }

    /**
     * 过滤后挂载 TolerantMcpToolCallbackProvider；无可用连接则跳过。
     */
    @Override
    public void attachTo(ChatClient.Builder builder) {
        attachTo(builder, Set.of());
    }

    /** 可按连接名再收窄挂载面，例如仅 crawler。 */
    @Override
    public void attachTo(ChatClient.Builder builder, Set<String> connectionNames) {
        List<McpSyncClient> allowed = filterClients(clients);
        if (!CollectionUtils.isEmpty(connectionNames)) {
            allowed = allowed.stream()
                    .filter(client -> matchesAny(client, connectionNames))
                    .toList();
        }
        if (CollectionUtils.isEmpty(allowed)) {
            log.debug("MCP Bridge：无可用连接，跳过工具挂载");
            return;
        }
        builder.defaultToolCallbacks(new TolerantMcpToolCallbackProvider(allowed, prefixGenerator));
        log.debug("MCP Bridge：已挂载 {} 个连接的工具回调", allowed.size());
    }

    /** 连接描述是否命中任一过滤名。 */
    private boolean matchesAny(McpSyncClient client, Set<String> connectionNames) {
        String describe = TolerantMcpToolCallbackProvider.describe(client);
        for (String name : connectionNames) {
            if (matchesConnection(describe, name)
                    || (name != null && name.toLowerCase(Locale.ROOT).contains("crawler")
                    && describe != null
                    && describe.toLowerCase(Locale.ROOT).contains("crawler"))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 按 database / crawler / smithery 子开关与 API Key 过滤客户端。
     */
    List<McpSyncClient> filterClients(List<McpSyncClient> source) {
        if (CollectionUtils.isEmpty(source)) {
            return List.of();
        }
        List<McpSyncClient> out = new ArrayList<>();
        for (McpSyncClient client : source) {
            if (isAllowed(client)) {
                out.add(client);
            } else {
                log.info("MCP Bridge：跳过连接 [{}]（子开关关闭或缺少 API Key）",
                        TolerantMcpToolCallbackProvider.describe(client));
            }
        }
        return out;
    }

    /**
     * 判断单个连接是否应参与工具发现。
     */
    boolean isAllowed(McpSyncClient client) {
        String name = TolerantMcpToolCallbackProvider.describe(client);
        String databaseName = properties.getDatabase().getConnectionName();
        String crawlerName = properties.getCrawler().getConnectionName();
        String smitheryName = properties.getSmithery().getConnectionName();

        // 1) 数据库 MCP：按子开关挂载
        if (matchesConnection(name, databaseName) || TolerantMcpToolCallbackProvider.isDatabase(client)) {
            return properties.getDatabase().isEnabled();
        }
        // 2) 爬虫 MCP：按子开关挂载
        if (matchesConnection(name, crawlerName)) {
            return properties.getCrawler().isEnabled();
        }
        // 3) Smithery：开关开且有 key 才挂载
        if (matchesConnection(name, smitheryName) || TolerantMcpToolCallbackProvider.isSmithery(client)) {
            if (!properties.getSmithery().isEnabled()) {
                return false;
            }
            if (!StringUtils.hasText(properties.getSmithery().getApiKey())) {
                log.warn("MCP Bridge：Smithery 已启用但未配置 SMITHERY_API_KEY，跳过连接 [{}]", name);
                return false;
            }
            return true;
        }
        // 4) 未知连接：总开关已开则保留，由 Tolerant 自行跳过失败
        return true;
    }

    /** connection 名是否命中（忽略大小写，允许子串）。 */
    private static boolean matchesConnection(String describe, String connectionName) {
        if (!StringUtils.hasText(describe) || !StringUtils.hasText(connectionName)) {
            return false;
        }
        return describe.equalsIgnoreCase(connectionName)
                || describe.toLowerCase().contains(connectionName.toLowerCase());
    }
}
