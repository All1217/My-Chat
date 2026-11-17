package com.mychat.agent.capability;

import com.mychat.tools.WebSearchTools;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** 提供与具体 Worker 解耦的受限并行网页研究能力。 */
@Component
public class WebResearchCapability {

    private static final int MAX_QUERIES = 4;
    private final WebSearchTools webSearchTools;

    /** 注入只读网页搜索工具。 */
    public WebResearchCapability(WebSearchTools webSearchTools) {
        this.webSearchTools = webSearchTools;
    }

    /** 并行执行最多四个查询，并按输入顺序汇总证据。 */
    public ResearchResult research(List<String> queries) {
        List<String> safeQueries = queries == null ? List.of() : queries.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .limit(MAX_QUERIES)
                .toList();
        if (safeQueries.isEmpty()) {
            throw new IllegalArgumentException("网页研究查询不能为空");
        }
        List<CompletableFuture<String>> tasks = safeQueries.stream()
                .map(query -> CompletableFuture.supplyAsync(
                        () -> webSearchTools.searchWeb(query)))
                .toList();
        List<String> evidence = tasks.stream().map(CompletableFuture::join).toList();
        return new ResearchResult(safeQueries, evidence);
    }

    /** 网页研究结果。 */
    public record ResearchResult(List<String> queries, List<String> evidence) {

        /** 渲染为 Planner 可读观察。 */
        public String render() {
            StringBuilder text = new StringBuilder();
            for (int index = 0; index < queries.size(); index++) {
                text.append("【研究主题 ").append(index + 1).append("】")
                        .append(queries.get(index)).append('\n')
                        .append(evidence.get(index)).append("\n\n");
            }
            return text.toString().trim();
        }
    }
}
