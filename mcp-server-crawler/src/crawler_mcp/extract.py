"""HTML 正文抽取：trafilatura 优先，失败则简易去标签。"""

from __future__ import annotations

import re
from html import unescape
from typing import Any

_TAG_RE = re.compile(r"<[^>]+>", re.DOTALL)
_WS_RE = re.compile(r"\s+")


def extract_main(html: str, url: str | None = None, max_chars: int = 50_000) -> dict[str, Any]:
    """从 HTML 抽取 title + text；返回 dict。"""
    raw = html or ""
    title = _guess_title(raw)
    text = ""
    # trafilatura 需 lxml_html_clean；失败则回退去标签
    try:
        import trafilatura

        extracted = trafilatura.extract(
            raw,
            url=url,
            include_comments=False,
            include_tables=True,
            favor_recall=True,
        )
        if extracted:
            text = extracted.strip()
    except Exception:
        text = ""
    if not text:
        text = _strip_tags(raw)
    text = _clip(text, max_chars)
    title = _clip(title, 500)
    return {"title": title, "text": text}


def _guess_title(html: str) -> str:
    m = re.search(r"<title[^>]*>(.*?)</title>", html, re.IGNORECASE | re.DOTALL)
    if not m:
        return ""
    return _WS_RE.sub(" ", unescape(_TAG_RE.sub("", m.group(1)))).strip()


def _strip_tags(html: str) -> str:
    # 去掉 script/style 再剥标签
    cleaned = re.sub(r"(?is)<(script|style).*?>.*?</\1>", " ", html)
    cleaned = _TAG_RE.sub(" ", cleaned)
    return _WS_RE.sub(" ", unescape(cleaned)).strip()


def _clip(s: str, max_chars: int) -> str:
    if max_chars <= 0 or len(s) <= max_chars:
        return s
    return s[: max_chars - 1] + "…"
