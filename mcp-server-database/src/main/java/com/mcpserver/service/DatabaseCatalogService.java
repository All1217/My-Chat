package com.mcpserver.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 通过 information_schema / pg_catalog 查询库元数据。 */
@Service
public class DatabaseCatalogService {

    private static final int MAX_DESCRIBE_TABLES = 20;

    private final JdbcTemplate jdbcTemplate;
    private final ConnectionInfoService connectionInfoService;

    public DatabaseCatalogService(JdbcTemplate jdbcTemplate, ConnectionInfoService connectionInfoService) {
        this.jdbcTemplate = jdbcTemplate;
        this.connectionInfoService = connectionInfoService;
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

    /** 列出指定 schema 下的表（含表注释）。 */
    public List<Map<String, Object>> listTables(String schema) {
        String target = StringUtils.hasText(schema) ? schema : "public";
        return jdbcTemplate.queryForList("""
                SELECT t.table_schema,
                       t.table_name,
                       t.table_type,
                       obj_description((quote_ident(t.table_schema) || '.' || quote_ident(t.table_name))::regclass) AS table_comment
                FROM information_schema.tables t
                WHERE t.table_schema = ?
                ORDER BY t.table_name
                """, target);
    }

    /**
     * 加厚表描述：列注释、主键、索引、外键。
     */
    public Map<String, Object> describeTable(String schema, String table) {
        if (!StringUtils.hasText(table)) {
            throw new IllegalArgumentException("table 不能为空");
        }
        String targetSchema = StringUtils.hasText(schema) ? schema : "public";
        String qualified = targetSchema + "." + table;

        List<Map<String, Object>> columns = jdbcTemplate.queryForList("""
                SELECT c.ordinal_position AS ordinal,
                       c.column_name,
                       c.data_type,
                       c.is_nullable,
                       c.column_default,
                       c.character_maximum_length,
                       col_description((quote_ident(c.table_schema) || '.' || quote_ident(c.table_name))::regclass,
                                       c.ordinal_position) AS comment
                FROM information_schema.columns c
                WHERE c.table_schema = ? AND c.table_name = ?
                ORDER BY c.ordinal_position
                """, targetSchema, table);
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("未找到表 " + qualified);
        }

        String tableComment = jdbcTemplate.query("""
                SELECT obj_description(format('%I.%I', ?, ?)::regclass)
                """, rs -> rs.next() ? rs.getString(1) : null, targetSchema, table);

        List<String> primaryKey = jdbcTemplate.queryForList("""
                SELECT kcu.column_name
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu
                  ON tc.constraint_name = kcu.constraint_name
                 AND tc.table_schema = kcu.table_schema
                WHERE tc.table_schema = ?
                  AND tc.table_name = ?
                  AND tc.constraint_type = 'PRIMARY KEY'
                ORDER BY kcu.ordinal_position
                """, String.class, targetSchema, table);

        List<Map<String, Object>> indexes = jdbcTemplate.queryForList("""
                SELECT i.relname AS index_name,
                       ix.indisunique AS is_unique,
                       array_to_string(array_agg(a.attname ORDER BY x.n), ',') AS columns
                FROM pg_class t
                JOIN pg_namespace n ON n.oid = t.relnamespace
                JOIN pg_index ix ON t.oid = ix.indrelid
                JOIN pg_class i ON i.oid = ix.indexrelid
                JOIN LATERAL unnest(ix.indkey) WITH ORDINALITY AS x(attnum, n) ON true
                JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = x.attnum
                WHERE n.nspname = ? AND t.relname = ? AND t.relkind = 'r'
                GROUP BY i.relname, ix.indisunique
                ORDER BY i.relname
                """, targetSchema, table);

        List<Map<String, Object>> foreignKeys = jdbcTemplate.queryForList("""
                SELECT kcu.column_name,
                       ccu.table_schema AS foreign_table_schema,
                       ccu.table_name AS foreign_table_name,
                       ccu.column_name AS foreign_column_name,
                       tc.constraint_name
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu
                  ON tc.constraint_name = kcu.constraint_name
                 AND tc.table_schema = kcu.table_schema
                JOIN information_schema.constraint_column_usage ccu
                  ON ccu.constraint_name = tc.constraint_name
                 AND ccu.table_schema = tc.table_schema
                WHERE tc.constraint_type = 'FOREIGN KEY'
                  AND tc.table_schema = ?
                  AND tc.table_name = ?
                ORDER BY tc.constraint_name, kcu.ordinal_position
                """, targetSchema, table);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("schema", targetSchema);
        body.put("table", table);
        body.put("tableComment", tableComment);
        body.put("columns", columns);
        body.put("primaryKey", primaryKey);
        body.put("indexes", indexes);
        body.put("foreignKeys", foreignKeys);
        return body;
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
        body.put("schema", StringUtils.hasText(schema) ? schema : "public");
        body.put("count", tables.size());
        body.put("tables", tables);
        return body;
    }

    /** Flyway 迁移历史；表不存在时抛出明确错误。 */
    public Map<String, Object> flywayStatus() {
        Boolean exists = jdbcTemplate.query("""
                SELECT to_regclass('public.flyway_schema_history') IS NOT NULL
                """, rs -> {
            rs.next();
            return rs.getBoolean(1);
        });
        if (!Boolean.TRUE.equals(exists)) {
            throw new IllegalStateException("未找到 public.flyway_schema_history（当前库可能尚未被 Flyway 接管）");
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT installed_rank, version, description, type, success, installed_on
                FROM flyway_schema_history
                ORDER BY installed_rank DESC
                LIMIT 20
                """);
        String latest = rows.isEmpty() ? null : String.valueOf(rows.get(0).get("version"));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("latestVersion", latest);
        body.put("count", rows.size());
        body.put("rows", rows);
        return body;
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
