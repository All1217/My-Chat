"""URL 安全校验单测。"""

import pytest

from crawler_mcp.config import CrawlerConfig
from crawler_mcp.safety import UrlSafetyError, validate_url


def test_rejects_file_scheme():
    with pytest.raises(UrlSafetyError):
        validate_url("file:///etc/passwd", CrawlerConfig())


def test_rejects_loopback():
    with pytest.raises(UrlSafetyError):
        validate_url("http://127.0.0.1/secret", CrawlerConfig())


def test_rejects_private_ip():
    with pytest.raises(UrlSafetyError):
        validate_url("http://192.168.1.1/", CrawlerConfig())


def test_allow_hosts_blocks_others():
    cfg = CrawlerConfig(allow_hosts={"example.com"})
    with pytest.raises(UrlSafetyError):
        validate_url("https://other.com/", cfg)


def test_https_ok_without_dns_if_not_resolved():
    # example.com 公网可解析；若离线可能失败，仅断言协议通过字面校验路径
    cfg = CrawlerConfig()
    try:
        out = validate_url("https://example.com/path", cfg)
        assert out.startswith("https://")
    except UrlSafetyError as e:
        # 离线无 DNS 时跳过
        if "无法解析" not in str(e):
            raise
