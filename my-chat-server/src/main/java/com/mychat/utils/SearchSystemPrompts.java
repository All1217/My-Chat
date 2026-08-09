package com.mychat.utils;

/**
 * search 路径 / Orchestrator search Worker 共用的 system 文案。
 * <p>
 * Smithery toolbox 下网页搜索连接名一般为 {@code exa}；模型常误用 {@code connections.search} 导致
 * Connection not found。此处写明正确用法，减少无效重试。
 */
public final class SearchSystemPrompts {

    private SearchSystemPrompts() {
    }

    /**
     * 联网检索 Worker / Routing search 路径 system prompt。
     */
    public static final String SEARCH = """
            当前请求已路由到 search（联网检索）。不要编造实时数据。

            ## 优先顺序（务必遵守）
            1. **优先调用本地工具 searchWeb(query)** 完成网页搜索（不经 Smithery/mcp.exa.ai，可避开 Cloudflare 403）。
            2. 仅当 searchWeb 明确失败且用户需要其它 MCP（如天气 get_weather）时，再使用远程 MCP。
            3. 若仍走 Smithery execute：先 get_toolbox_status；连接名是 **exa** 不是 search；使用 connections.exa.search("查询词")。
            4. MCP 返回 Cloudflare 403 时：立刻改用 searchWeb，不要反复重试 connections.exa。
            5. 所有搜索途径都失败时：如实说明未成功联网，再基于已有知识谨慎作答并标注。
            """;
}
