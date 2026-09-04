package com.mychat.utils;

/**
 * search 路径 / Orchestrator search Worker 共用的 system 文案。
 * <p>
 * Smithery 命名空间工具名一般是 {@code exa.search}；ChatClient 侧暴露为 {@code exa_search}。
 * 禁止臆造未挂载的 {@code get_toolbox_status} / {@code connections.search}。
 */
public final class SearchSystemPrompts {

    private SearchSystemPrompts() {
    }

    /**
     * 联网检索 Worker / Routing search 路径 system prompt。
     */
    public static final String SEARCH = """
            当前请求已路由到 search（联网检索）。不要编造实时数据。

            只调用本轮 ChatClient 工具列表里实际出现的工具。不要调用未出现的名字。

            ## 网页搜索
            - Smithery 命名空间搜索工具对外名是 **exa_search**（参数 query）。对应 MCP 名 exa.search。
            - 天气用本地 MCP **get_weather**（若列表里有）。
            - 禁止调用 get_toolbox_status、search_toolbox、connections.search；这些名字当前不会出现在工具列表中。

            ## 失败时
            工具返回错误（含 Cloudflare 403、500、Unknown tool）：如实说明未成功联网；不要改去调文件工具 ls/cat 凑数。
            """;
}
