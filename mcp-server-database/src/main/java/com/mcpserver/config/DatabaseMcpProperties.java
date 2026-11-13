package com.mcpserver.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 数据库 MCP 运行约束（前缀 app.database）。 */
@Data
@ConfigurationProperties(prefix = "app.database")
public class DatabaseMcpProperties {

    /** 是否只读；true 时仅允许 SELECT / WITH / EXPLAIN。 */
    private boolean readOnly = true;

    /** 查询结果最大行数。 */
    private int maxRows = 200;

    /** JDBC 查询超时（秒）。 */
    private int queryTimeoutSeconds = 15;
}
