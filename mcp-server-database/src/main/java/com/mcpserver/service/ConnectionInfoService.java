package com.mcpserver.service;

import com.mcpserver.config.DatabaseMcpProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/** 从 datasource URL 解析连接摘要，供各工具响应附带。 */
@Service
public class ConnectionInfoService {

    private final DatabaseMcpProperties properties;
    private final String jdbcUrl;

    public ConnectionInfoService(DatabaseMcpProperties properties,
                                 @Value("${spring.datasource.url}") String jdbcUrl) {
        this.properties = properties;
        this.jdbcUrl = jdbcUrl != null ? jdbcUrl : "";
    }

    /** 工具响应里的 connection 块。 */
    public Map<String, Object> snapshot() {
        Parsed parsed = parse(jdbcUrl);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("label", properties.getConnectionLabel());
        out.put("jdbcHost", parsed.host());
        out.put("jdbcPort", parsed.port());
        out.put("jdbcDatabase", parsed.database());
        out.put("schemaHint", parsed.schemaHint());
        out.put("readOnly", properties.isReadOnly());
        out.put("maxRows", properties.getMaxRows());
        out.put("maxCellLength", properties.getMaxCellLength());
        out.put("queryTimeoutSeconds", properties.getQueryTimeoutSeconds());
        return out;
    }

    /** 仅连接与策略，给 connection_info 工具。 */
    public Map<String, Object> fullInfo() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("kind", "connection");
        out.put("connection", snapshot());
        out.put("note", "本 MCP 仅连本地开发库；云库请用 Adminer/SSH，勿假设此处是生产数据");
        return out;
    }

    /** 解析 jdbc:postgresql://host:port/db?currentSchema=public */
    static Parsed parse(String url) {
        if (!StringUtils.hasText(url)) {
            return new Parsed("", null, "", "public");
        }
        try {
            String normalized = url.startsWith("jdbc:") ? url.substring(5) : url;
            URI uri = URI.create(normalized);
            String host = uri.getHost() != null ? uri.getHost() : "";
            Integer port = uri.getPort() >= 0 ? uri.getPort() : null;
            String path = uri.getPath() != null ? uri.getPath() : "";
            String database = path.startsWith("/") ? path.substring(1) : path;
            if (database.contains("/")) {
                database = database.substring(0, database.indexOf('/'));
            }
            String schemaHint = "public";
            String query = uri.getQuery();
            if (StringUtils.hasText(query)) {
                for (String part : query.split("&")) {
                    int eq = part.indexOf('=');
                    if (eq > 0 && "currentSchema".equalsIgnoreCase(part.substring(0, eq))) {
                        schemaHint = part.substring(eq + 1);
                        break;
                    }
                }
            }
            return new Parsed(host, port, database, schemaHint);
        } catch (Exception e) {
            return new Parsed("unparsed", null, "", "public");
        }
    }

    record Parsed(String host, Integer port, String database, String schemaHint) {
    }
}
