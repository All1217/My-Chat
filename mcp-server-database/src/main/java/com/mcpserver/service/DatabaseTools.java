package com.mcpserver.service;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

import java.util.Map;

/** 对外暴露的数据库 MCP 工具。 */
@Service
public class DatabaseTools {

    private final DatabaseCatalogService catalogService;
    private final DatabaseQueryService queryService;

    public DatabaseTools(DatabaseCatalogService catalogService, DatabaseQueryService queryService) {
        this.catalogService = catalogService;
        this.queryService = queryService;
    }

    /** 列出非系统 schema。 */
    @McpTool(name = "list_schemas", description = "列出当前数据库中的非系统 schema")
    public Map<String, Object> listSchemas() {
        return catalogService.wrap("schemas", catalogService.listSchemas());
    }

    /** 列出指定 schema 下的表。 */
    @McpTool(name = "list_tables", description = "列出指定 schema 下的表；schema 为空时默认 public")
    public Map<String, Object> listTables(
            @McpToolParam(description = "schema 名称，可选，默认 public", required = false) String schema) {
        return catalogService.wrap("tables", catalogService.listTables(schema));
    }

    /** 描述表结构。 */
    @McpTool(name = "describe_table", description = "查看指定表的列名、类型、是否可空与默认值")
    public Map<String, Object> describeTable(
            @McpToolParam(description = "schema 名称，可选，默认 public", required = false) String schema,
            @McpToolParam(description = "表名", required = true) String table) {
        return catalogService.wrap("columns", catalogService.describeTable(schema, table));
    }

    /** 执行受只读策略约束的 SQL。 */
    @McpTool(name = "execute_sql",
            description = "执行 SQL。默认只读，仅允许 SELECT/WITH/EXPLAIN；read-only=false 时才允许写。禁止多语句。")
    public Map<String, Object> executeSql(
            @McpToolParam(description = "单条 SQL 语句", required = true) String sql) {
        return queryService.execute(sql);
    }
}
