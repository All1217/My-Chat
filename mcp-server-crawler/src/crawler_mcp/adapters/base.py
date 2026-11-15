"""站点适配协议。B 阶段实现巨潮等时遵循此接口，无需改 MCP 工具签名。"""

from __future__ import annotations

from typing import Any, Protocol, runtime_checkable

from crawler_mcp.config import CrawlerConfig
from crawler_mcp.http_client import HttpFetcher


@runtime_checkable
class SiteAdapter(Protocol):
    """
    站点适配插件位。
    - name: 稳定标识（如 generic / cninfo）
    - match(url): 是否接管该 URL
    - fetch(url, fetcher, cfg): 返回与 fetch_url 相同结构的 dict
    """

    name: str

    def match(self, url: str) -> bool:
        """URL 是否由本适配器处理。"""
        ...

    def fetch(self, url: str, fetcher: HttpFetcher, cfg: CrawlerConfig) -> dict[str, Any]:
        """抓取并抽取；失败时 ok=False 且带 error。"""
        ...
