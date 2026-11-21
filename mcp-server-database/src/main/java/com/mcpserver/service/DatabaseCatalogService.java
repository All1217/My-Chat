package com.mcpserver.service;

import com.mcpserver.catalog.CatalogDialect;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 通过方言查询库元数据（PostgreSQL / MySQL）。 */
@Service
public class DatabaseCatalogService {

    private static final int MAX_DESCRIBE_TABLES = 20;

    private final CatalogDialect catalogDialect;
    private final ConnectionInfoService connectionInfoService;

    public DatabaseCatalogService(CatalogDialect catalogDialect, ConnectionInfoService connectionInfoService) {
        this.catalogDialect = catalogDialect;
        this.connectionInfoService = connectionInfoService;
    }

    /** 列出非系统 schema（MySQL 下为 database）。 */
    public List<Map<String, Object>> listSchemas() {
        return catalogDialect.listSchemas();
    }

    /** 列出指定 schema 下的表（含表注释）。 */
    public List<Map<String, Object>> listTables(String schema) {
        return catalogDialect.listTables(schema);
    }

    /** 加厚表描述：列注释、主键、索引、外键。 */
    public Map<String, Object> describeTable(String schema, String table) {
        return catalogDialect.describeTable(schema, table);
    }

    /** 批量描述；tables 为逗号分隔，单次最多 20 张。 */
    public Map<String, Object> describeTables(String schema, String tablesCsv) {
        if (!StringUtils.hasText(tablesCsv)) {
            throw new IllegalArgumentException("tables 不能为空（逗号分隔表名）");
        }
        List<String> names = Arrays.stream(tablesCsv.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
        if (names.isEmpty()) {
            throw new IllegalArgumentException("tables 不能为空（逗号分隔表名）");
        }
        if (names.size() > MAX_DESCRIBE_TABLES) {
            throw new IllegalArgumentException("单次最多描述 " + MAX_DESCRIBE_TABLES + " 张表，当前 " + names.size());
        }
        List<Map<String, Object>> tables = new ArrayList<>();
        for (String name : names) {
            tables.add(describeTable(schema, name));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("schema", StringUtils.hasText(schema) ? schema : catalogDialect.defaultSchema());
        body.put("count", tables.size());
        body.put("tables", tables);
        return body;
    }

    /** Flyway 迁移历史；无表时 available=false。 */
    public Map<String, Object> flywayStatus() {
        return catalogDialect.flywayStatus();
    }

    /** 将列表结果包成统一结构并附 connection。 */
    public Map<String, Object> wrap(String kind, List<Map<String, Object>> rows) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("kind", kind);
        out.put("count", rows.size());
        out.put("rows", rows);
        out.put("connection", connectionInfoService.snapshot());
        return out;
    }

    /** 将对象结果包成统一结构并附 connection。 */
    public Map<String, Object> wrapObject(String kind, Map<String, Object> body) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("kind", kind);
        out.putAll(body);
        out.put("connection", connectionInfoService.snapshot());
        return out;
    }
}
