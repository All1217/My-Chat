package com.mcpserver.service;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

import java.util.Map;

/** 对外暴露的数据库 MCP 工具（本地/内网开发库 PG 或 MySQL；云库请用 Adminer/SSH）。 */
@Service
public class DatabaseTools {

    private final DatabaseCatalogService catalogService;
    private final DatabaseQueryService queryService;
    private final ConnectionInfoService connectionInfoService;

    public DatabaseTools(DatabaseCatalogService catalogService,
                         DatabaseQueryService queryService,
                         ConnectionInfoService connectionInfoService) {
        this.catalogService = catalogService;
        this.queryService = queryService;
        this.connectionInfoService = connectionInfoService;
    }

    /** 当前 JDBC 连接摘要与只读/行数策略。 */
    @McpTool(name = "connection_info",
            description = "返回本 MCP 当前连接的库（本地开发库）与只读/截断策略；云排障请用 Adminer/SSH")
    public Map<String, Object> connectionInfo() {
        return connectionInfoService.fullInfo();
    }

    /** 列出非系统 schema（MySQL 下为 database）。 */
    @McpTool(name = "list_schemas", description = "列出非系统 schema；MySQL 下返回非系统 database 列表")
    public Map<String, Object> listSchemas() {
        return catalogService.wrap("schemas", catalogService.listSchemas());
    }

    /** 列出指定 schema 下的表（含表注释）。 */
    @McpTool(name = "list_tables",
            description = "列出指定 schema/database 下的表（含 table_comment）；为空时 PG 默认 public，MySQL 默认当前库")
    public Map<String, Object> listTables(
            @McpToolParam(description = "schema（PG）或 database（MySQL），可选", required = false) String schema) {
        return catalogService.wrap("tables", catalogService.listTables(schema));
    }

    /** 加厚单表描述。 */
    @McpTool(name = "describe_table",
            description = "查看表结构：列（含注释）、主键、索引、外键、表注释")
    public Map<String, Object> describeTable(
            @McpToolParam(description = "schema 名称，可选，默认 public", required = false) String schema,
            @McpToolParam(description = "表名", required = true) String table) {
        return catalogService.wrapObject("table", catalogService.describeTable(schema, table));
    }

    /** 批量加厚描述。 */
    @McpTool(name = "describe_tables",
            description = "批量描述多张表（tables 为逗号分隔，单次最多 20 张）")
    public Map<String, Object> describeTables(
            @McpToolParam(description = "schema 名称，可选，默认 public", required = false) String schema,
            @McpToolParam(description = "逗号分隔表名，如 llm_model,chat_sessions", required = true) String tables) {
        return catalogService.wrapObject("tables_detail", catalogService.describeTables(schema, tables));
    }

    /** Flyway 最近迁移。 */
    @McpTool(name = "flyway_status",
            description = "查询 flyway_schema_history 最近 20 条及 latestVersion；表不存在时 available=false")
    public Map<String, Object> flywayStatus() {
        return catalogService.wrapObject("flyway", catalogService.flywayStatus());
    }

    /** 执行受只读策略约束的 SQL。 */
    @McpTool(name = "execute_sql",
            description = "执行 SQL。默认只读（SELECT/WITH/EXPLAIN/SHOW/DESC）；结果敏感列脱敏、超长单元格截断。禁止多语句。")
    public Map<String, Object> executeSql(
            @McpToolParam(description = "单条 SQL 语句", required = true) String sql,
            @McpToolParam(description = "本次最大行数，不超过服务端 max-rows", required = false) Integer maxRows) {
        return queryService.execute(sql, maxRows);
    }
}
