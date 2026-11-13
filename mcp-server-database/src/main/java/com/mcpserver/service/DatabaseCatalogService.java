package com.mcpserver.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 通过 information_schema 查询库元数据。 */
@Service
public class DatabaseCatalogService {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseCatalogService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 列出非系统 schema。 */
    public List<Map<String, Object>> listSchemas() {
        return jdbcTemplate.queryForList("""
                SELECT schema_name
                FROM information_schema.schemata
                WHERE schema_name NOT IN ('pg_catalog', 'information_schema', 'pg_toast')
                ORDER BY schema_name
                """);
    }

    /** 列出指定 schema 下的表；schema 为空时默认 public。 */
    public List<Map<String, Object>> listTables(String schema) {
        String target = StringUtils.hasText(schema) ? schema : "public";
        return jdbcTemplate.queryForList("""
                SELECT table_schema, table_name, table_type
                FROM information_schema.tables
                WHERE table_schema = ?
                ORDER BY table_name
                """, target);
    }

    /** 描述表列定义。 */
    public List<Map<String, Object>> describeTable(String schema, String table) {
        if (!StringUtils.hasText(table)) {
            throw new IllegalArgumentException("table 不能为空");
        }
        String targetSchema = StringUtils.hasText(schema) ? schema : "public";
        List<Map<String, Object>> columns = jdbcTemplate.queryForList("""
                SELECT column_name, data_type, is_nullable, column_default, character_maximum_length
                FROM information_schema.columns
                WHERE table_schema = ? AND table_name = ?
                ORDER BY ordinal_position
                """, targetSchema, table);
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("未找到表 " + targetSchema + "." + table);
        }
        return columns;
    }

    /** 将结果包成统一 JSON 友好结构。 */
    public Map<String, Object> wrap(String kind, List<Map<String, Object>> rows) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("kind", kind);
        out.put("count", rows.size());
        out.put("rows", rows);
        return out;
    }
}
