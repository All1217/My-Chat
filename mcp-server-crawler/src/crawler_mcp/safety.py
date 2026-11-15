"""URL 安全校验：防 SSRF、协议限制、可选 host 白名单。"""

from __future__ import annotations

import ipaddress
import socket
from urllib.parse import urlparse

from crawler_mcp.config import CrawlerConfig


class UrlSafetyError(ValueError):
    """不安全或非法 URL。"""


def validate_url(url: str, cfg: CrawlerConfig) -> str:
    """
    校验并规范化 URL。
    仅允许 http/https；拒绝内网/链路本地/元数据地址；可选 allow_hosts。
    """
    if not url or not str(url).strip():
        raise UrlSafetyError("url 不能为空")
    raw = str(url).strip()
    parsed = urlparse(raw)
    scheme = (parsed.scheme or "").lower()
    if scheme not in ("http", "https"):
        raise UrlSafetyError("仅允许 http/https（禁止 file 等本地协议）")
    host = (parsed.hostname or "").strip().lower()
    if not host:
        raise UrlSafetyError("缺少 host")
    if cfg.allow_hosts and host not in cfg.allow_hosts:
        raise UrlSafetyError(f"host 不在 CRAWLER_ALLOW_HOSTS 白名单: {host}")
    _reject_private_host(host)
    return raw


def _reject_private_host(host: str) -> None:
    """解析 host；任一地址落在私网/保留段则拒绝。"""
    # 字面 IP
    try:
        ip = ipaddress.ip_address(host)
        if _is_blocked_ip(ip):
            raise UrlSafetyError(f"拒绝内网或保留地址: {host}")
        return
    except ValueError:
        pass

    # 域名：解析全部 A/AAAA
    try:
        infos = socket.getaddrinfo(host, None)
    except socket.gaierror as e:
        raise UrlSafetyError(f"无法解析 host: {host}") from e
    if not infos:
        raise UrlSafetyError(f"无法解析 host: {host}")
    for info in infos:
        addr = info[4][0]
        try:
            ip = ipaddress.ip_address(addr)
        except ValueError:
            continue
        if _is_blocked_ip(ip):
            raise UrlSafetyError(f"host 解析到内网或保留地址: {host} -> {addr}")


def _is_blocked_ip(ip: ipaddress.IPv4Address | ipaddress.IPv6Address) -> bool:
    """私网、环回、链路本地、未指定、组播、保留、云元数据常见段。"""
    if (
        ip.is_private
        or ip.is_loopback
        or ip.is_link_local
        or ip.is_unspecified
        or ip.is_multicast
        or ip.is_reserved
    ):
        return True
    # AWS/GCP 元数据
    if isinstance(ip, ipaddress.IPv4Address) and ip == ipaddress.ip_address("169.254.169.254"):
        return True
    return False
