"""站点适配插件包。V1 仅 generic；B 阶段可加 cninfo 等。"""

from crawler_mcp.adapters.registry import AdapterRegistry, default_registry

__all__ = ["AdapterRegistry", "default_registry"]
