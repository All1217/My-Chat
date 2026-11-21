package com.mcpserver.catalog;

import java.util.List;
import java.util.Map;

/** 库目录元数据方言：PostgreSQL / MySQL。 */
public interface CatalogDialect {

    /** 方言名：postgresql | mysql。 */
    String name();

    /** schema / database 缺省名（PG 多为 public；MySQL 为当前库）。 */
    String defaultSchema();

    /** 列出非系统 schema（MySQL 下为 database）。 */
    List<Map<String, Object>> listSchemas();

    /** 列出指定 schema 下的表（含 table_comment）。 */
    List<Map<String, Object>> listTables(String schema);

    /** 加厚单表描述。 */
    Map<String, Object> describeTable(String schema, String table);

    /**
     * Flyway 状态；无历史表时返回 available=false，不抛硬错误。
     */
    Map<String, Object> flywayStatus();
}
