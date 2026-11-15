# mcp-server-database

PostgreSQL 元数据 / SQL MCP 服务（Spring AI 2.0 Streamable HTTP）。

**范围：仅连本机开发库。** 云上生产/部署库请用 Adminer + SSH 隧道（见 [`docs/部署与运维指引.md`](../docs/部署与运维指引.md)），不要假设本服务能看到云库。

## 能力

| 工具 | 说明 |
|------|------|
| `connection_info` | 当前 JDBC 主机/库/只读与截断策略（开局自检） |
| `list_schemas` | 列出非系统 schema |
| `list_tables` | 列出指定 schema 的表（含 `table_comment`） |
| `describe_table` | 列（含注释）、主键、索引、外键、表注释 |
| `describe_tables` | 批量描述（逗号分隔表名，单次最多 20） |
| `flyway_status` | `flyway_schema_history` 最近 20 条 + `latestVersion` |
| `execute_sql` | 执行 SQL；默认只读；敏感列脱敏、超长单元格截断 |

所有工具响应附带 `connection`：`label` / `jdbcHost` / `jdbcDatabase` / `schemaHint` / `readOnly` 等。

## 启动

```powershell
$env:PGSQL_PASS="你的密码"
cd mcp-server-database
.\mvnw.cmd spring-boot:run
```

默认监听 `http://localhost:8102/mcp`。

### 启动失败常见原因

| 现象 | 处理 |
|------|------|
| 连不上库 / 认证失败 | 检查 `PGSQL_PASS`、本机 Postgres 是否在 `localhost:5432` |
| 端口占用 | 改 `server.port` 或释放 8102 |
| Cursor 看不到工具 | 确认本服务已起；检查 `.cursor/mcp.json`；Customize → MCP 刷新；必要时 `mcp_auth` |

自检：Agent 先调 `connection_info`，确认 `label=local` 且 `jdbcHost` 符合预期。

## 配置（`application.yaml`）

| 项 | 默认 | 说明 |
|----|------|------|
| `app.database.read-only` | `true` | `false` 才允许写（仍禁多语句与危险 DDL） |
| `app.database.max-rows` | `200` | 全局最大行数；`execute_sql.maxRows` 不可超过此值 |
| `app.database.max-cell-length` | `500` | 字符串单元格截断 |
| `app.database.connection-label` | `local` | 写入响应 `connection.label` |
| `app.database.sensitive-column-patterns` | api_key 等 | 列名包含即脱敏 |
| `app.database.query-timeout-seconds` | `15` | 查询超时 |

## 接入 Cursor

见 [`docs/Cursor本地MCP接入提醒.md`](../docs/Cursor本地MCP接入提醒.md)。

## 接入 my-chat-server

主应用通过 `spring.ai.mcp.client.streamable-http.connections.mcp-database-server` 连接本服务，并由 `app.mcp.database.enabled` 控制是否挂载工具。云上主应用通常 `APP_MCP_ENABLED=false`，与本 Cursor 侧 MCP 无关。

## 手工冒烟清单

1. 启动本服务与本地 Postgres。  
2. `connection_info` → `label=local`。  
3. `describe_table`（如 `llm_model`）→ 有列注释与 `primaryKey`。  
4. `execute_sql`：`SELECT api_key FROM llm_model LIMIT 1` → 值为脱敏而非明文。  
5. `flyway_status` → 有 `latestVersion`。  
