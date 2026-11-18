# mcp-server-crawler

项目内通用网页抓取 MCP（FastMCP Streamable HTTP）。从公开 HTML 抽取标题与正文，补充搜集侧「只有标题+URL」的缺口。

**不是**东财/新浪行情 JSON 客户端；K 线、列表、公告标题列表仍由 `my-chat-server` 内现有 HTTP 客户端负责。

## 能力

| 工具 | 说明 |
|------|------|
| `fetch_url` | 抓取单页并抽取正文 |
| `fetch_urls` | 批量抓取（默认最多 5） |
| `extract_main` | 对已有 HTML 抽正文（不联网） |
| `list_adapters` | 已注册站点适配器（V1 仅 `generic`） |

站点适配插件位见 `src/crawler_mcp/adapters/`；后续巨潮等全文适配可 `register` 而不改工具签名。

## 启动

```powershell
cd mcp-server-crawler
.\scripts\run.ps1
```

或：

```powershell
cd mcp-server-crawler
python -m pip install -e ".[dev]"
python -m crawler_mcp.server
```

默认监听 `http://localhost:8103/mcp`。

可选环境变量见 [`.env.example`](.env.example)。

## Docker（云上常驻）

Compose 服务见 [`deploy/docker-compose.yml`](../deploy/docker-compose.yml) 的 `crawler`：仅 Docker 内网 `expose: 8103`，不对公网映射。

```bash
docker compose -f deploy/docker-compose.yml --env-file /data/my-chat/.env up -d crawler
```

主应用设 `MCP_CRAWLER_URL=http://crawler:8103`，并保持 `APP_MCP_DATABASE_ENABLED=false`、`APP_MCP_SMITHERY_ENABLED=false`。详见 [`docs/部署与运维指引.md`](../docs/部署与运维指引.md)。

## 接入 Cursor

`.cursor/` 被 gitignore，**每台机器自行**在项目根 `.cursor/mcp.json` 增加：

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

先启动本服务，再在 Cursor → Customize → MCP 中确认工具可见。换机提醒见 [`docs/Cursor本地MCP接入提醒.md`](../docs/Cursor本地MCP接入提醒.md)。

## 接入 my-chat-server

主应用通过 `spring.ai.mcp.client.streamable-http.connections.mcp-crawler-server` 连接，并由 `app.mcp.crawler.enabled` 控制是否挂载。

## 测试

```powershell
cd mcp-server-crawler
python -m pip install -e ".[dev]"
python -m pytest -q
```
