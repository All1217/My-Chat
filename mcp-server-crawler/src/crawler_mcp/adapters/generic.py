"""通用 HTML 适配器：任意公网页面 → 标题 + 正文。"""

from __future__ import annotations

from typing import Any

from crawler_mcp.config import CrawlerConfig
from crawler_mcp.extract import extract_main
from crawler_mcp.http_client import HttpFetcher, decode_body, result_error


class GenericAdapter:
    """V1 默认适配器；不匹配东财/新浪 JSON 行情接口场景。"""

    name = "generic"

    def match(self, url: str) -> bool:
        # 兜底：注册表在无专用适配时选用本适配
        return True

    def fetch(self, url: str, fetcher: HttpFetcher, cfg: CrawlerConfig) -> dict[str, Any]:
        raw = fetcher.get(url)
        if not raw.ok:
            return result_error(url, raw.error or "fetch failed", self.name)

        # 非文本也尽量解码；PDF 等留给 B 阶段专用适配
        html = decode_body(raw.body, raw.content_type)
        extracted = extract_main(html, url=raw.final_url, max_chars=cfg.max_text_chars)
        return {
            "ok": True,
            "status": raw.status,
            "url": url,
            "final_url": raw.final_url,
            "content_type": raw.content_type,
            "title": extracted.get("title") or "",
            "text": extracted.get("text") or "",
            "adapter": self.name,
            "error": None,
        }
