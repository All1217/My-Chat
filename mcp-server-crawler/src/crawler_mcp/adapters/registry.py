"""适配器注册表：按 URL 解析；B 阶段 register(CninfoAdapter()) 即可。"""

from __future__ import annotations

from crawler_mcp.adapters.base import SiteAdapter
from crawler_mcp.adapters.generic import GenericAdapter


class AdapterRegistry:
    """有序注册；先匹配专用适配，否则 generic。"""

    def __init__(self) -> None:
        self._adapters: list[SiteAdapter] = []
        self._generic = GenericAdapter()

    def register(self, adapter: SiteAdapter) -> None:
        """注册站点适配（勿重复注册 generic）。"""
        if adapter.name == self._generic.name:
            return
        self._adapters.append(adapter)

    def resolve(self, url: str) -> SiteAdapter:
        """解析 URL 对应适配器。"""
        for adapter in self._adapters:
            if adapter.match(url):
                return adapter
        return self._generic

    def list_adapters(self) -> list[dict[str, str]]:
        """列出已注册适配器（含 generic）。"""
        rows = [{"name": a.name, "role": "site"} for a in self._adapters]
        rows.append({"name": self._generic.name, "role": "fallback"})
        return rows


def default_registry() -> AdapterRegistry:
    """V1：仅 generic；预留 B 注册位点。"""
    reg = AdapterRegistry()
    # --- B 扩展点示例（勿在 V1 启用）---
    # from crawler_mcp.adapters.cninfo import CninfoAdapter
    # reg.register(CninfoAdapter())
    return reg
