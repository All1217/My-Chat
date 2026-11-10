package com.mychat.mcp;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.McpToolNamePrefixGenerator;
import org.springframework.ai.mcp.customizer.McpClientCustomizer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * MCP 模块统一装配：仅在 {@code app.mcp.enabled=true} 时生效。
 */
@Configuration
@ConditionalOnProperty(prefix = "app.mcp", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(McpProperties.class)
public class McpModuleConfiguration {

    private static final Logger log = LoggerFactory.getLogger(McpModuleConfiguration.class);

    /**
     * 为 Smithery streamable-http 连接注入 Bearer；无 key 时只 warn。
     */
    @Bean
    public McpClientCustomizer<HttpClientStreamableHttpTransport.Builder> smitheryAuthCustomizer(
            McpProperties properties) {
        return (name, transportBuilder) -> {
            String connectionName = properties.getSmithery().getConnectionName();
            // 只给 Smithery 连接加 Bearer，避免影响本地天气等无鉴权 MCP
            if (!connectionName.equals(name)) {
                return;
            }
            if (!properties.getSmithery().isEnabled()) {
                log.info("MCP 连接 [{}] 已通过 app.mcp.smithery.enabled=false 关闭鉴权注册", name);
                return;
            }
            String apiKey = properties.getSmithery().getApiKey();
            if (!StringUtils.hasText(apiKey)) {
                log.warn("SMITHERY_API_KEY / app.mcp.smithery.api-key 未配置；连接 {} 跳过 Bearer（Bridge 亦会跳过该连接）",
                        name);
                return;
            }
            transportBuilder.httpRequestCustomizer(
                    (req, method, endpoint, body, ctx) ->
                            req.header("Authorization", "Bearer " + apiKey));
            log.info("已为 MCP 连接 [{}] 注册 Smithery Bearer 鉴权", name);
        };
    }

    /**
     * 主干 ChatClientAssembler 通过此桥挂载 MCP 工具。
     */
    @Bean
    public McpToolBridge mcpToolBridge(McpProperties properties,
                                       ObjectProvider<List<McpSyncClient>> mcpSyncClients,
                                       ObjectProvider<McpToolNamePrefixGenerator> prefixGenerator) {
        List<McpSyncClient> clients = mcpSyncClients.getIfAvailable();
        if (CollectionUtils.isEmpty(clients)) {
            log.warn("MCP 模块已启用但未发现 McpSyncClient；检查 spring.ai.mcp.client.enabled 与 connections");
            clients = List.of();
        }
        return new DefaultMcpToolBridge(properties, clients, prefixGenerator.getIfAvailable());
    }

    /**
     * 启动诊断：失败只打日志，不抛出。
     */
    @Bean
    public ApplicationRunner mcpToolsDiagnostics(McpProperties properties,
                                                 ObjectProvider<List<McpSyncClient>> mcpSyncClients,
                                                 ObjectProvider<McpToolNamePrefixGenerator> prefixGenerator) {
        List<McpSyncClient> clients = mcpSyncClients.getIfAvailable();
        return new McpToolsDiagnostics(
                properties,
                clients == null ? List.of() : clients,
                prefixGenerator.getIfAvailable());
    }
}
