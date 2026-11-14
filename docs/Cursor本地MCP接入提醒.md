# Cursor 本地 MCP 接入提醒

`.cursor/` 目录被 `.gitignore` 忽略，**不会**随仓库同步。换机或新克隆后，若要用本地 `mcp-server-database`，需在本机自行创建配置。

## 步骤

1. 启动数据库 MCP（需 `PGSQL_PASS` 等环境变量）：

```powershell
cd mcp-server-database
.\mvnw.cmd spring-boot:run
```

默认地址：`http://localhost:8102/mcp`。

2. 在项目根创建 `.cursor/mcp.json`（仅本机）：

```json
{
  "mcpServers": {
    "mcp-database-server": {
      "url": "http://localhost:8102/mcp"
    }
  }
}
```

3. 重载 Cursor MCP（Customize → MCP，或重开窗口），确认 `mcp-database-server` 工具可见。

服务说明见 [`mcp-server-database/README.md`](../mcp-server-database/README.md)。
