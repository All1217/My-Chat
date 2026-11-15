"""
FastMCP Streamable HTTP 入口。
监听默认 http://0.0.0.0:8103/mcp；不替代项目内行情 JSON 接口。
"""

from __future__ import annotations

import atexit
from typing import Any

from fastmcp import FastMCP

from crawler_mcp.config import CrawlerConfig
from crawler_mcp.service import CrawlerService

cfg = CrawlerConfig.from_env()
service = CrawlerService(cfg)
atexit.register(service.close)

mcp = FastMCP(
    name="mcp-crawler-server",
    instructions=(
        "项目内通用网页抓取 MCP：从公开 HTML 页面抽取标题与正文，"
        "用于补充公告/新闻落地页全文。"
        "不要用本工具替代东财/新浪等行情与列表 JSON 接口；遵守目标站 ToS 与频率限制。"
    ),
)


@mcp.tool(
    name="fetch_url",
    description=(
        "抓取单个公开网页并抽取正文。"
        "返回 status/final_url/title/text/content_type/adapter。"
        "不用于行情 K 线或股票列表 API。"
    ),
)
def fetch_url(url: str) -> dict[str, Any]:
    """抓取单 URL。"""
    return service.fetch_url(url)


@mcp.tool(
    name="fetch_urls",
    description=(
        "批量抓取网页（默认最多 5 个）；单项失败不影响其余。"
        "参数 urls 为 URL 字符串数组。"
    ),
)
def fetch_urls(urls: list[str]) -> dict[str, Any]:
    """批量抓取。"""
    return service.fetch_urls(urls)


@mcp.tool(
    name="extract_main",
    description="对已有 HTML 字符串抽取标题与正文（不发起网络请求）。",
)
def extract_main_tool(html: str, url: str | None = None) -> dict[str, Any]:
    """HTML → 正文。"""
    return service.extract_main(html, url=url)


@mcp.tool(
    name="list_adapters",
    description="列出已注册站点适配器；V1 仅 generic，后续可扩展巨潮等。",
)
def list_adapters() -> dict[str, Any]:
    """适配器清单。"""
    return service.list_adapters()


def main() -> None:
    """启动 Streamable HTTP 服务。"""
    mcp.run(
        transport="streamable-http",
        host=cfg.host,
        port=cfg.port,
        path=cfg.path,
    )


if __name__ == "__main__":
    main()
