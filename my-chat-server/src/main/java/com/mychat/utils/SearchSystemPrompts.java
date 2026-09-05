package com.mychat.utils;

/**
 * search 路径 / Orchestrator search Worker 共用的 system 文案。
 * <p>
 * 网页搜索优先本地 {@code searchWeb}（Exa REST）。Smithery {@code exa_search} 仅作备选。
 * 禁止臆造未挂载的 {@code get_toolbox_status} / {@code connections.search}。
 */
public final class SearchSystemPrompts {

    /**
     * 工具类，禁止实例化。
     */
    private SearchSystemPrompts() {
    }

    /**
     * 联网检索 Worker / Routing search 路径 system prompt。
     */
    public static final String SEARCH = """
            当前请求已路由到 search（联网检索）。不要编造实时数据。

            只调用本轮 ChatClient 工具列表里实际出现的工具。不要调用未出现的名字。

            ## 网页搜索
            - 优先调用 **searchWeb**（参数 query）。本机直连 Exa REST，不经 Smithery。
            - 天气用本地 MCP **get_weather**（若列表里有）。
            - **exa_search** 仅当工具列表里实际出现时才可作为备选；不要臆造。
            - 禁止调用 get_toolbox_status、search_toolbox、connections.search、connections.exa。

            ## 失败时
            工具返回 401/403/空结果或 Invalid API key：如实说明未成功联网；不要改去调文件工具 ls/cat 凑数。
            """;
}
