# mcp-server-database

PostgreSQL 元数据 / SQL MCP 服务（Spring AI 2.0 Streamable HTTP）。

## 能力

| 工具 | 说明 |
|------|------|
| `list_schemas` | 列出非系统 schema |
| `list_tables` | 列出指定 schema 的表（默认 `public`） |
| `describe_table` | 查看表列定义 |
| `execute_sql` | 执行 SQL；默认只读，仅 `SELECT` / `WITH` / `EXPLAIN` |

## 启动

```powershell
$env:PGSQL_PASS="你的密码"
cd mcp-server-database
.\mvnw.cmd spring-boot:run
```

默认监听 `http://localhost:8102/mcp`。

主要配置见 `src/main/resources/application.yaml`：

- `spring.datasource.*`：数据库地址与账号
- `app.database.read-only`：默认 `true`；设为 `false` 才允许写
- `app.database.max-rows` / `query-timeout-seconds`：结果行数与超时

## 接入 Cursor

`.cursor/` 被 gitignore，**每台机器需自行**在项目根创建 `.cursor/mcp.json`：

```json
{
  "mcpServers": {
    "mcp-database-server": {
      "url": "http://localhost:8102/mcp"
    }
  }
}
```

先启动本服务，再在 Cursor → Customize → MCP 中确认工具可见。CLI 若要求 `type`，可写 `"type": "http"`。换机提醒见 [`docs/Cursor本地MCP接入提醒.md`](../docs/Cursor本地MCP接入提醒.md)。

## 接入 my-chat-server

主应用通过 `spring.ai.mcp.client.streamable-http.connections.mcp-database-server` 连接本服务，并由 `app.mcp.database.enabled` 控制是否挂载工具。
