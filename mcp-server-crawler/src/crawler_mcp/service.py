"""抓取编排：适配器解析 + 批量。"""

from __future__ import annotations

from typing import Any

from crawler_mcp.adapters.registry import AdapterRegistry, default_registry
from crawler_mcp.config import CrawlerConfig
from crawler_mcp.extract import extract_main
from crawler_mcp.http_client import HttpFetcher, result_error


class CrawlerService:
    """对外业务入口（供 MCP 工具调用）。"""

    def __init__(self, cfg: CrawlerConfig | None = None, registry: AdapterRegistry | None = None) -> None:
        self.cfg = cfg or CrawlerConfig.from_env()
        self.registry = registry or default_registry()
        self.fetcher = HttpFetcher(self.cfg)

    def close(self) -> None:
        self.fetcher.close()

    def fetch_url(self, url: str) -> dict[str, Any]:
        """单 URL 抓取。"""
        adapter = self.registry.resolve(url)
        try:
            return adapter.fetch(url, self.fetcher, self.cfg)
        except Exception as e:
            return result_error(url, f"{type(e).__name__}: {e}", getattr(adapter, "name", "generic"))

    def fetch_urls(self, urls: list[str]) -> dict[str, Any]:
        """批量抓取；单项失败不中断。"""
        limit = max(1, self.cfg.batch_limit)
        items = list(urls or [])[:limit]
        truncated = len(urls or []) > limit
        results = [self.fetch_url(u) for u in items]
        return {
            "count": len(results),
            "truncated": truncated,
            "batch_limit": limit,
            "results": results,
        }

    def extract_main(self, html: str, url: str | None = None) -> dict[str, Any]:
        """对已有 HTML 抽正文。"""
        extracted = extract_main(html or "", url=url, max_chars=self.cfg.max_text_chars)
        return {
            "ok": True,
            "title": extracted.get("title") or "",
            "text": extracted.get("text") or "",
            "adapter": "extract_only",
            "error": None,
        }

    def list_adapters(self) -> dict[str, Any]:
        return {"adapters": self.registry.list_adapters()}
