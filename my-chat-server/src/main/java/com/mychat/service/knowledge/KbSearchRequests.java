package com.mychat.service.knowledge;

import org.springframework.ai.vectorstore.SearchRequest;

/**
 * 按知识库构造向量检索请求（问答 Advisor 与召回测试共用滤条件）。
 */
public final class KbSearchRequests {

    private KbSearchRequests() {
    }

    /**
     * 已设 topK、阈值与 kbId 过滤；query 由 Advisor 或召回测试再填。
     */
    public static SearchRequest.Builder filtered(String kbId, int topK, double threshold) {
        String id = kbId == null ? "" : kbId.trim();
        return SearchRequest.builder()
                .topK(topK)
                .similarityThreshold(threshold)
                .filterExpression("kbId == '" + id + "'");
    }
}
