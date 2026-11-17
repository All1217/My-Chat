package com.mychat.mcp.settings;

import com.mychat.mcp.settings.entity.po.McpConnectionSettings;
import com.mychat.mcp.settings.entity.vo.McpToolCatalogItemVO;
import com.mychat.mcp.settings.mapper.McpConnectionSettingsMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP/Exa 运行时开关：DB 落库 + 内存缓存，改完立即对工具发现生效。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpRuntimeSettingsService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final McpConnectionSettingsMapper mapper;

    /** id → enabled */
    private final ConcurrentHashMap<String, Boolean> cache = new ConcurrentHashMap<>();

    @PostConstruct
    public void warm() {
        try {
            reload();
        } catch (Exception e) {
            log.warn("mcp_connection_settings 预热失败，默认全开: {}", e.getMessage());
            seedDefaultsInMemory();
        }
    }

    /** 从库重载缓存；缺行则补种。 */
    public synchronized void reload() {
        ensureRows();
        List<McpConnectionSettings> rows = mapper.selectList(null);
        cache.clear();
        for (McpConnectionSettings row : rows) {
            cache.put(row.getId(), Boolean.TRUE.equals(row.getEnabled()));
        }
        seedDefaultsInMemory();
    }

    /** 某连接是否启用（默认 true）。 */
    public boolean isEnabled(String id) {
        if (!StringUtils.hasText(id)) {
            return true;
        }
        Boolean v = cache.get(id);
        if (v != null) {
            return v;
        }
        // 兼容 title/name 子串
        for (Map.Entry<String, Boolean> e : cache.entrySet()) {
            if (id.equalsIgnoreCase(e.getKey())
                    || id.toLowerCase().contains(e.getKey().toLowerCase())
                    || e.getKey().toLowerCase().contains(id.toLowerCase())) {
                return Boolean.TRUE.equals(e.getValue());
            }
        }
        return true;
    }

    /** 更新启用状态并刷新缓存。 */
    @Transactional
    public McpToolCatalogItemVO setEnabled(String id, boolean enabled) {
        if (!catalog().containsKey(id)) {
            throw new IllegalArgumentException("未知连接: " + id);
        }
        ensureRows();
        McpConnectionSettings row = mapper.selectById(id);
        if (row == null) {
            row = new McpConnectionSettings();
            row.setId(id);
            row.setEnabled(enabled);
            row.setUpdatedAt(LocalDateTime.now());
            mapper.insert(row);
        } else {
            row.setEnabled(enabled);
            row.setUpdatedAt(LocalDateTime.now());
            mapper.updateById(row);
        }
        cache.put(id, enabled);
        return toVo(id, row);
    }

    /** 设置页目录。 */
    public List<McpToolCatalogItemVO> listCatalog() {
        ensureRows();
        List<McpToolCatalogItemVO> out = new ArrayList<>();
        for (String id : catalog().keySet()) {
            McpConnectionSettings row = mapper.selectById(id);
            out.add(toVo(id, row));
        }
        return out;
    }

    private McpToolCatalogItemVO toVo(String id, McpConnectionSettings row) {
        CatalogMeta meta = catalog().get(id);
        McpToolCatalogItemVO vo = new McpToolCatalogItemVO();
        vo.setId(id);
        vo.setName(meta.name());
        vo.setType(meta.type());
        vo.setDescription(meta.description());
        vo.setTools(List.copyOf(meta.tools()));
        boolean enabled = row != null ? Boolean.TRUE.equals(row.getEnabled()) : isEnabled(id);
        vo.setEnabled(enabled);
        if (row != null && row.getUpdatedAt() != null) {
            vo.setUpdatedAt(row.getUpdatedAt().format(TS));
        }
        return vo;
    }

    private void ensureRows() {
        for (String id : catalog().keySet()) {
            if (mapper.selectById(id) == null) {
                McpConnectionSettings row = new McpConnectionSettings();
                row.setId(id);
                row.setEnabled(true);
                row.setUpdatedAt(LocalDateTime.now());
                try {
                    mapper.insert(row);
                } catch (Exception e) {
                    log.debug("seed {} 跳过: {}", id, e.getMessage());
                }
            }
        }
    }

    private void seedDefaultsInMemory() {
        for (String id : catalog().keySet()) {
            cache.putIfAbsent(id, true);
        }
    }

    private static Map<String, CatalogMeta> catalog() {
        Map<String, CatalogMeta> m = new LinkedHashMap<>();
        m.put(McpConnectionSettings.ID_DATABASE, new CatalogMeta(
                "本地数据库 MCP",
                "local-mcp",
                "PostgreSQL 元数据与只读 SQL，供 Cursor / 主聊天核表。",
                List.of("list_schemas", "list_tables", "describe_table", "execute_sql")));
        m.put(McpConnectionSettings.ID_CRAWLER, new CatalogMeta(
                "本地网页抓取 MCP",
                "local-mcp",
                "公开 HTML 正文抽取；搜集员发现候选阶段与 Agent 可按需使用。",
                List.of("fetch_url", "fetch_urls", "extract_main", "list_adapters")));
        m.put(McpConnectionSettings.ID_SMITHERY, new CatalogMeta(
                "Smithery 工具箱",
                "remote-mcp",
                "远端命名空间 MCP（含历史 Exa 等聚合工具）；需 SMITHERY_API_KEY。",
                List.of("exa_search（命名空间）", "其他已连接工具")));
        m.put(McpConnectionSettings.ID_EXA, new CatalogMeta(
                "Exa Search（REST）",
                "rest",
                "本机 web-search 工具链首选 Exa REST（非 Smithery 网关）。",
                List.of("searchWeb")));
        return m;
    }

    private record CatalogMeta(String name, String type, String description, List<String> tools) {
    }
}
