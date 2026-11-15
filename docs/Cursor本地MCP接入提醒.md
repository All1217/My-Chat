# Cursor 本地 MCP 接入提醒

`.cursor/` 目录被 `.gitignore` 忽略，**不会**随仓库同步。换机或新克隆后，若要用本地 `mcp-server-database`，需在本机自行创建配置。

## 适用范围（重要）

| 场景 | 用什么 |
|------|--------|
| 本地核表结构、核对开发库数据、Flyway 版本 | **本 MCP**（`localhost:8102`） |
| 云上部署库排障、改云上 `.env` 相关数据 | **Adminer + SSH 隧道**（见 [部署与运维指引](./部署与运维指引.md)） |

Agent 提示词里「优先调用 MCP」= **优先查本地开发库**，不是云库。响应里的 `connection.label` / `jdbcHost` 可核对是否连对。

## 步骤

1. 启动数据库 MCP（需 `PGSQL_PASS` 等环境变量）：

```powershell
cd mcp-server-database
$env:PGSQL_PASS="你的密码"
.\mvnw.cmd spring-boot:run
```

默认地址：`http://localhost:8102/mcp`。

可选：启动网页抓取 MCP（Python，端口 8103）：

```powershell
cd mcp-server-crawler
.\scripts\run.ps1
```

默认地址：`http://localhost:8103/mcp`。

2. 在项目根创建 `.cursor/mcp.json`（仅本机）：

```json
{
  "mcpServers": {
    "mcp-database-server": {
      "url": "http://localhost:8102/mcp"
    },
    "mcp-crawler-server": {
      "url": "http://localhost:8103/mcp"
    }
  }
}
```

3. 重载 Cursor MCP（Customize → MCP，或重开窗口），确认工具可见。数据库 MCP 建议先调 `connection_info` 做自检。

服务说明见 [`mcp-server-database/README.md`](../mcp-server-database/README.md)、[`mcp-server-crawler/README.md`](../mcp-server-crawler/README.md)。
