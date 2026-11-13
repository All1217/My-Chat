package com.mychat.mcp;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MCP 模块开关与连接名（前缀 {@code app.mcp}）。
 */
@Data
@ConfigurationProperties(prefix = "app.mcp")
public class McpProperties {

    /** 总开关；false 时不装配本模块 Bean，且应同步关闭 spring.ai.mcp.client.enabled。 */
    private boolean enabled = true;

    /** 本地数据库 MCP（mcp-server-database）连接开关。 */
    private Database database = new Database();

    /** Smithery 命名空间 MCP 连接与鉴权。 */
    private Smithery smithery = new Smithery();

    /** 数据库连接子配置。 */
    @Data
    public static class Database {
        /** 是否挂载数据库 MCP 工具。 */
        private boolean enabled = true;
        /** YAML connection 名，默认 mcp-database-server。 */
        private String connectionName = "mcp-database-server";
    }

    /** Smithery 连接子配置。 */
    @Data
    public static class Smithery {
        /** 是否挂载 Smithery MCP 工具。 */
        private boolean enabled = true;
        /** Bearer API Key；空则跳过该连接。 */
        private String apiKey = "";
        /** YAML connection 名，默认 smithery-toolbox。 */
        private String connectionName = "smithery-toolbox";
    }
}
