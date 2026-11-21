package com.mcpserver.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/** 数据库 MCP 运行约束（前缀 app.database）。 */
@Data
@ConfigurationProperties(prefix = "app.database")
public class DatabaseMcpProperties {

    /** 是否只读；true 时仅允许 SELECT / WITH / EXPLAIN / SHOW / DESC。 */
    private boolean readOnly = true;

    /** 方言：postgresql | mysql（由 profile 写入）。 */
    private String dialect = "postgresql";

    /** 查询结果最大行数。 */
    private int maxRows = 200;

    /** JDBC 查询超时（秒）。 */
    private int queryTimeoutSeconds = 15;

    /** 单单元格字符串最大长度，超出截断。 */
    private int maxCellLength = 500;

    /** 连接环境标签（响应 connection.label），默认 local。 */
    private String connectionLabel = "local";

    /** 是否 MySQL 方言。 */
    public boolean isMysql() {
        return "mysql".equalsIgnoreCase(dialect);
    }

    /**
     * 敏感列名子串（大小写不敏感）；列名包含任一则脱敏。
     * 默认 api_key / password / secret / token / credential。
     */
    private List<String> sensitiveColumnPatterns = new ArrayList<>(List.of(
            "api_key", "password", "secret", "token", "credential"));
}
