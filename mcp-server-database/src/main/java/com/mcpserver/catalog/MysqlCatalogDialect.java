package com.mcpserver.catalog;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** MySQL：information_schema；schema 语义对齐为 database。 */
public class MysqlCatalogDialect implements CatalogDialect {

    private final JdbcTemplate jdbcTemplate;

    public MysqlCatalogDialect(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String name() {
        return "mysql";
    }

    @Override
    public String defaultSchema() {
        try {
            String db = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
            return db != null ? db : "";
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public List<Map<String, Object>> listSchemas() {
        return jdbcTemplate.queryForList("""
                SELECT SCHEMA_NAME AS schema_name
                FROM information_schema.SCHEMATA
                WHERE SCHEMA_NAME NOT IN ('mysql', 'information_schema', 'performance_schema', 'sys')
                ORDER BY SCHEMA_NAME
                """);
    }

    @Override
    public List<Map<String, Object>> listTables(String schema) {
        String target = resolveSchema(schema);
        if (!StringUtils.hasText(target)) {
            throw new IllegalArgumentException("未选定 database；请传 schema 参数或在 JDBC URL 中指定库名");
        }
        return jdbcTemplate.queryForList("""
                SELECT t.TABLE_SCHEMA AS table_schema,
                       t.TABLE_NAME AS table_name,
                       t.TABLE_TYPE AS table_type,
                       t.TABLE_COMMENT AS table_comment
                FROM information_schema.TABLES t
                WHERE t.TABLE_SCHEMA = ?
                ORDER BY t.TABLE_NAME
                """, target);
    }

    @Override
    public Map<String, Object> describeTable(String schema, String table) {
        if (!StringUtils.hasText(table)) {
            throw new IllegalArgumentException("table 不能为空");
        }
        String targetSchema = resolveSchema(schema);
        if (!StringUtils.hasText(targetSchema)) {
            throw new IllegalArgumentException("未选定 database；请传 schema 参数或在 JDBC URL 中指定库名");
        }
        String qualified = targetSchema + "." + table;

        List<Map<String, Object>> columns = jdbcTemplate.queryForList("""
                SELECT c.ORDINAL_POSITION AS ordinal,
                       c.COLUMN_NAME AS column_name,
                       c.DATA_TYPE AS data_type,
                       c.IS_NULLABLE AS is_nullable,
                       c.COLUMN_DEFAULT AS column_default,
                       c.CHARACTER_MAXIMUM_LENGTH AS character_maximum_length,
                       c.COLUMN_COMMENT AS comment
                FROM information_schema.COLUMNS c
                WHERE c.TABLE_SCHEMA = ? AND c.TABLE_NAME = ?
                ORDER BY c.ORDINAL_POSITION
                """, targetSchema, table);
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("未找到表 " + qualified);
        }

        String tableComment = jdbcTemplate.query("""
                SELECT TABLE_COMMENT
                FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?
                """, rs -> rs.next() ? rs.getString(1) : null, targetSchema, table);

        List<String> primaryKey = jdbcTemplate.queryForList("""
                SELECT COLUMN_NAME
                FROM information_schema.KEY_COLUMN_USAGE
                WHERE TABLE_SCHEMA = ?
                  AND TABLE_NAME = ?
                  AND CONSTRAINT_NAME = 'PRIMARY'
                ORDER BY ORDINAL_POSITION
                """, String.class, targetSchema, table);

        List<Map<String, Object>> indexes = jdbcTemplate.queryForList("""
                SELECT INDEX_NAME AS index_name,
                       CASE WHEN NON_UNIQUE = 0 THEN 1 ELSE 0 END AS is_unique,
                       GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') AS columns
                FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?
                GROUP BY INDEX_NAME, NON_UNIQUE
                ORDER BY INDEX_NAME
                """, targetSchema, table);

        List<Map<String, Object>> foreignKeys = jdbcTemplate.queryForList("""
                SELECT kcu.COLUMN_NAME AS column_name,
                       kcu.REFERENCED_TABLE_SCHEMA AS foreign_table_schema,
                       kcu.REFERENCED_TABLE_NAME AS foreign_table_name,
                       kcu.REFERENCED_COLUMN_NAME AS foreign_column_name,
                       kcu.CONSTRAINT_NAME AS constraint_name
                FROM information_schema.KEY_COLUMN_USAGE kcu
                WHERE kcu.TABLE_SCHEMA = ?
                  AND kcu.TABLE_NAME = ?
                  AND kcu.REFERENCED_TABLE_NAME IS NOT NULL
                ORDER BY kcu.CONSTRAINT_NAME, kcu.ORDINAL_POSITION
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

    @Override
    public Map<String, Object> flywayStatus() {
        String schema = defaultSchema();
        Map<String, Object> body = new LinkedHashMap<>();
        if (!StringUtils.hasText(schema)) {
            body.put("available", false);
            body.put("message", "未选定 database，无法查询 flyway_schema_history");
            body.put("latestVersion", null);
            body.put("count", 0);
            body.put("rows", List.of());
            return body;
        }
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = ? AND TABLE_NAME = 'flyway_schema_history'
                """, Integer.class, schema);
        if (count == null || count == 0) {
            body.put("available", false);
            body.put("message", "未找到 " + schema + ".flyway_schema_history");
            body.put("latestVersion", null);
            body.put("count", 0);
            body.put("rows", List.of());
            return body;
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT installed_rank, version, description, type, success, installed_on
                FROM flyway_schema_history
                ORDER BY installed_rank DESC
                LIMIT 20
                """);
        String latest = rows.isEmpty() ? null : String.valueOf(rows.get(0).get("version"));
        body.put("available", true);
        body.put("latestVersion", latest);
        body.put("count", rows.size());
        body.put("rows", rows);
        return body;
    }

    private String resolveSchema(String schema) {
        return StringUtils.hasText(schema) ? schema : defaultSchema();
    }
}
