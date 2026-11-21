# mcp-server-database

本地/内网开发库元数据与 SQL MCP（Spring AI Streamable HTTP）。支持 **PostgreSQL** 与 **MySQL**（一次进程只连一种库）。

**范围：开发与内网库。** 云上生产库请用 Adminer + SSH 隧道（见 [`docs/部署与运维指引.md`](../docs/部署与运维指引.md)）。

默认端点：`http://localhost:8102/mcp`（Cursor / OpenCode / DeepSeek Harness 等凡支持 URL 型 MCP 的客户端均可接入）。

## 能力

| 工具 | 说明 |
|------|------|
| `connection_info` | 当前 JDBC 主机/库/方言/只读与截断策略（开局自检） |
| `list_schemas` | 列出非系统 schema；MySQL 下为非系统 database |
| `list_tables` | 列出指定 schema/database 的表（含 `table_comment`） |
| `describe_table` | 列（含注释）、主键、索引、外键、表注释 |
| `describe_tables` | 批量描述（逗号分隔表名，单次最多 20） |
| `flyway_status` | `flyway_schema_history` 最近 20 条 + `latestVersion`；无表则 `available=false` |
| `execute_sql` | 执行 SQL；默认只读（含 `SHOW`/`DESC`）；敏感列脱敏、超长截断 |

所有工具响应附带 `connection`：`label` / `dialect` / `jdbcHost` / `jdbcDatabase` / `schemaHint` / `readOnly` 等。

## 一键启动（Windows）

1. 复制 `.env.example` → `.env`，填入 `PGSQL_PASS` / `MYSQL_PASS` 等（**勿提交 `.env`**）。
2. 双击：
   - [`scripts/start-postgres.bat`](scripts/start-postgres.bat) → 连本机 PostgreSQL
   - [`scripts/start-mysql.bat`](scripts/start-mysql.bat) → 连 MySQL（默认见 `.env.example` 中的 JDBC URL）
3. 切换库：**先** [`scripts/stop.bat`](scripts/stop.bat)（或关控制台）→ **再**双击另一个 start。**不必改** `application.yaml`。

命令行等价：

```powershell
cd mcp-server-database
.\scripts\start-postgres.bat
# 或
.\scripts\start-mysql.bat
```

## 配置

| 项 | 默认 | 说明 |
|----|------|------|
| `DB_TYPE` | `postgres` | `postgres` \| `mysql`（启动脚本设置） |
| `app.database.read-only` | `true` | `false` 才允许写 |
| `app.database.max-rows` | `200` | 全局最大行数 |
| `PGSQL_*` / `MYSQL_*` | 见 `.env.example` | 账号与可选 JDBC URL 覆盖 |

Profile 文件：`application-postgres.yaml` / `application-mysql.yaml`。

## 接入 Cursor

见 [`docs/Cursor本地MCP接入提醒.md`](../docs/Cursor本地MCP接入提醒.md)。核心：

```json
{
  "mcpServers": {
    "mcp-database-server": {
      "url": "http://localhost:8102/mcp"
    }
  }
}
```

先启动本服务，再在 Cursor → Customize → MCP 确认工具可见。自检：先调 `connection_info`，确认 `dialect` 与 `jdbcHost`。

## 接入其他 AI 工具（OpenCode / DeepSeek Harness 等）

凡支持 **Streamable HTTP / URL MCP** 的客户端，填同一地址即可：

| 客户端 | 配置要点 |
|--------|----------|
| Cursor | `.cursor/mcp.json` → `"url": "http://localhost:8102/mcp"` |
| OpenCode | MCP servers 增加 type/url（或等价字段）指向上述地址 |
| DeepSeek Harness | 工具/MCP 配置里填 Streamable HTTP endpoint 同上 |

换 PG/MySQL 只重启本服务进程；客户端 URL **不用改**。

## 接入 my-chat-server

主应用通过 `spring.ai.mcp.client.streamable-http.connections.mcp-database-server` 连接，并由 `app.mcp.database.enabled` 控制是否挂载。云上默认只常驻 crawler（`APP_MCP_DATABASE_ENABLED=false`），本服务仍供本机开发。

## 手工冒烟清单

1. 双击 `start-postgres.bat` 或 `start-mysql.bat`。  
2. `connection_info` → `dialect` 与 `jdbcHost` 符合预期。  
3. `list_schemas` / `list_tables` / `describe_table`。  
4. `execute_sql`：只读 `SELECT` 或 `SHOW TABLES`；写语句应被拒绝。  
5. `flyway_status`：有表则有 `latestVersion`，无表则 `available=false`。
