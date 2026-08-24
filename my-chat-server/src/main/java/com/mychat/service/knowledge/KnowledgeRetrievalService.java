package com.mychat.service.knowledge;

import com.mychat.entity.dto.KnowledgeRetrieveHit;
import com.mychat.entity.dto.KnowledgeRetrieveTestRequest;
import com.mychat.entity.dto.KnowledgeRetrieveTestResponse;
import com.mychat.entity.po.KnowledgeBase;
import com.mychat.entity.po.KnowledgeBaseSettings;
import com.mychat.mapper.KnowledgeBaseMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 知识库召回测试：只做向量检索，不调用生成模型。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeRetrievalService {

    /** 模拟问题字数上限，避免超长 embedding */
    public static final int MAX_QUERY_CHARS = 1000;

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final VectorStore vectorStore;

    /**
     * 按 kbId 过滤检索；topK / 阈值未传则用该库已存值，不写库。
     */
    public KnowledgeRetrieveTestResponse retrieveTest(KnowledgeRetrieveTestRequest request) {
        // 解析参数：库必须存在；query 非空；检索项缺省用库设置
        if (request == null || !StringUtils.hasText(request.getKbId())) {
            throw new IllegalArgumentException("必须绑定知识库");
        }
        String kbId = request.getKbId().trim();
        KnowledgeBase kb = knowledgeBaseMapper.selectById(kbId);
        if (kb == null) {
            throw new IllegalArgumentException("知识库不存在");
        }
        if (!StringUtils.hasText(request.getQuery())) {
            throw new IllegalArgumentException("query 不能为空");
        }
        String query = request.getQuery().trim();
        if (query.isEmpty()) {
            throw new IllegalArgumentException("query 不能为空");
        }
        if (query.length() > MAX_QUERY_CHARS) {
            throw new IllegalArgumentException("query 不能超过 " + MAX_QUERY_CHARS + " 字");
        }
        int topK = request.getTopK() != null
                ? request.getTopK()
                : KnowledgeBaseSettings.topKOrDefault(kb.getTopK());
        double threshold = request.getSimilarityThreshold() != null
                ? request.getSimilarityThreshold()
                : KnowledgeBaseSettings.thresholdOrDefault(kb.getSimilarityThreshold());
        KnowledgeBaseSettings.validate(
                KnowledgeBaseSettings.DEFAULT_CHUNK_SIZE,
                KnowledgeBaseSettings.DEFAULT_CHUNK_OVERLAP,
                topK,
                threshold);

        // 检索：与问答 Advisor 同一套 kbId 过滤
        SearchRequest searchRequest = KbSearchRequests.filtered(kbId, topK, threshold)
                .query(query)
                .build();
        List<Document> docs = vectorStore.similaritySearch(searchRequest);
        if (docs == null) {
            docs = List.of();
        }
        log.info("召回测试 kbId={} topK={} threshold={} hits={}", kbId, topK, threshold, docs.size());

        // 映射 hits：分数与来源 metadata
        KnowledgeRetrieveTestResponse response = new KnowledgeRetrieveTestResponse();
        response.setKbId(kbId);
        response.setTopK(topK);
        response.setSimilarityThreshold(threshold);
        List<KnowledgeRetrieveHit> hits = new ArrayList<>(docs.size());
        for (Document doc : docs) {
            hits.add(toHit(doc));
        }
        response.setHits(hits);
        return response;
    }

    /** 把 VectorStore 文档转成前端可读的命中项。 */
    private static KnowledgeRetrieveHit toHit(Document doc) {
        KnowledgeRetrieveHit hit = new KnowledgeRetrieveHit();
        hit.setText(doc.getText());
        hit.setScore(scoreOf(doc));
        Map<String, Object> meta = doc.getMetadata();
        if (meta != null) {
            hit.setFilename(stringMeta(meta, "filename"));
            hit.setDocumentId(stringMeta(meta, "documentId"));
        }
        return hit;
    }

    private static Double scoreOf(Document doc) {
        if (doc.getScore() != null) {
            return doc.getScore();
        }
        Map<String, Object> meta = doc.getMetadata();
        if (meta == null) {
            return null;
        }
        Object raw = meta.get("score");
        if (raw == null) {
            raw = meta.get("distance");
        }
        if (raw instanceof Number n) {
            return n.doubleValue();
        }
        return null;
    }

    private static String stringMeta(Map<String, Object> meta, String key) {
        Object v = meta.get(key);
        return v == null ? null : String.valueOf(v);
    }
}
