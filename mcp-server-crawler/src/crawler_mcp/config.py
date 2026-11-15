"""爬虫运行配置（环境变量）。"""

from __future__ import annotations

import os
from dataclasses import dataclass, field


def _float(name: str, default: float) -> float:
    raw = os.getenv(name)
    if raw is None or not raw.strip():
        return default
    return float(raw)


def _int(name: str, default: int) -> int:
    raw = os.getenv(name)
    if raw is None or not raw.strip():
        return default
    return int(raw)


@dataclass
class CrawlerConfig:
    """抓取与服务参数。"""

    host: str = "0.0.0.0"
    port: int = 8103
    path: str = "/mcp"
    user_agent: str = "MyChatCrawler/1.0 (+local; not a market-data API client)"
    timeout_seconds: float = 15.0
    max_redirects: int = 5
    max_bytes: int = 2 * 1024 * 1024
    max_text_chars: int = 50_000
    rate_per_second: float = 2.0
    batch_limit: int = 5
    allow_hosts: set[str] = field(default_factory=set)

    @classmethod
    def from_env(cls) -> "CrawlerConfig":
        """从环境变量加载；缺省用计划约定值。"""
        allow_raw = os.getenv("CRAWLER_ALLOW_HOSTS", "").strip()
        allow = {h.strip().lower() for h in allow_raw.split(",") if h.strip()} if allow_raw else set()
        return cls(
            host=os.getenv("CRAWLER_HOST", "0.0.0.0").strip() or "0.0.0.0",
            port=_int("CRAWLER_PORT", 8103),
            timeout_seconds=_float("CRAWLER_TIMEOUT_SECONDS", 15.0),
            max_bytes=_int("CRAWLER_MAX_BYTES", 2 * 1024 * 1024),
            max_text_chars=_int("CRAWLER_MAX_TEXT_CHARS", 50_000),
            rate_per_second=_float("CRAWLER_RATE_PER_SECOND", 2.0),
            batch_limit=_int("CRAWLER_BATCH_LIMIT", 5),
            allow_hosts=allow,
        )
