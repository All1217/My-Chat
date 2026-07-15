package com.mychat.config;

import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.customizer.McpClientCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Smithery Namespace MCP 鉴权：为 streamable-http 连接 {@code smithery-toolbox}
 * 注入 {@code Authorization: Bearer <SMITHERY_API_KEY>}。
 * <p>
 * Spring AI 2.0 的 YAML connections 不支持 headers，需通过
 * {@link McpClientCustomizer} + {@code httpRequestCustomizer} 定制。
 */
@Configuration
public class SmitheryMcpConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SmitheryMcpConfiguration.class);

    @Bean
    public McpClientCustomizer<HttpClientStreamableHttpTransport.Builder> smitheryAuthCustomizer(
            @Value("${app.mcp.smithery.api-key:}") String apiKey,
            @Value("${app.mcp.smithery.connection-name:smithery-toolbox}") String connectionName) {
        return (name, transportBuilder) -> {
            // 只给 Smithery 连接加 Bearer，避免影响本地天气等无鉴权 MCP
            if (!connectionName.equals(name)) {
                return;
            }
            if (!StringUtils.hasText(apiKey)) {
                log.warn("SMITHERY_API_KEY / app.mcp.smithery.api-key 未配置；连接 {} 将以无鉴权方式请求（可能 401）",
                        name);
                return;
            }
            transportBuilder.httpRequestCustomizer(
                    (req, method, endpoint, body, ctx) ->
                            req.header("Authorization", "Bearer " + apiKey));
            log.info("已为 MCP 连接 [{}] 注册 Smithery Bearer 鉴权", name);
        };
    }
}
