"""httpx 抓取封装：超时、跳转、体积上限、全局限速。"""

from __future__ import annotations

import threading
import time
from dataclasses import dataclass
from typing import Any

import httpx

from crawler_mcp.config import CrawlerConfig
from crawler_mcp.safety import UrlSafetyError, validate_url


@dataclass
class FetchRawResult:
    """原始 HTTP 结果。"""

    ok: bool
    status: int | None
    final_url: str
    content_type: str
    body: bytes
    error: str | None = None


class RateLimiter:
    """简单全局限速（令牌间隔）。"""

    def __init__(self, rate_per_second: float) -> None:
        self._interval = 1.0 / rate_per_second if rate_per_second > 0 else 0.0
        self._lock = threading.Lock()
        self._next = 0.0

    def wait(self) -> None:
        if self._interval <= 0:
            return
        with self._lock:
            now = time.monotonic()
            delay = self._next - now
            if delay > 0:
                time.sleep(delay)
                now = time.monotonic()
            self._next = now + self._interval


class HttpFetcher:
    """带安全校验的 HTTP GET。"""

    def __init__(self, cfg: CrawlerConfig) -> None:
        self.cfg = cfg
        self._limiter = RateLimiter(cfg.rate_per_second)
        self._client = httpx.Client(
            follow_redirects=True,
            max_redirects=cfg.max_redirects,
            timeout=httpx.Timeout(cfg.timeout_seconds),
            headers={"User-Agent": cfg.user_agent, "Accept": "text/html,application/xhtml+xml,*/*;q=0.8"},
        )

    def close(self) -> None:
        self._client.close()

    def get(self, url: str) -> FetchRawResult:
        """GET 单 URL；校验每跳目标防 SSRF。"""
        try:
            current = validate_url(url, self.cfg)
        except UrlSafetyError as e:
            return FetchRawResult(False, None, url, "", b"", str(e))

        self._limiter.wait()
        try:
            # 逐步跟随以便校验每一跳
            resp = self._client.get(current)
            # httpx 已跟随；再校验最终 URL
            validate_url(str(resp.url), self.cfg)
            body = resp.content
            if len(body) > self.cfg.max_bytes:
                return FetchRawResult(
                    False,
                    resp.status_code,
                    str(resp.url),
                    resp.headers.get("content-type", ""),
                    b"",
                    f"响应超过 max_bytes={self.cfg.max_bytes}",
                )
            return FetchRawResult(
                True,
                resp.status_code,
                str(resp.url),
                resp.headers.get("content-type", ""),
                body,
                None,
            )
        except UrlSafetyError as e:
            return FetchRawResult(False, None, current, "", b"", str(e))
        except Exception as e:
            return FetchRawResult(False, None, current, "", b"", f"{type(e).__name__}: {e}")


def decode_body(body: bytes, content_type: str) -> str:
    """按 content-type / 嗅探解码为文本。"""
    charset = "utf-8"
    ct = (content_type or "").lower()
    if "charset=" in ct:
        charset = ct.split("charset=", 1)[1].split(";")[0].strip().strip("\"'") or "utf-8"
    try:
        return body.decode(charset, errors="replace")
    except LookupError:
        return body.decode("utf-8", errors="replace")


def result_error(url: str, message: str, adapter: str = "generic") -> dict[str, Any]:
    """统一错误返回结构。"""
    return {
        "ok": False,
        "status": None,
        "url": url,
        "final_url": url,
        "content_type": "",
        "title": "",
        "text": "",
        "adapter": adapter,
        "error": message,
    }
