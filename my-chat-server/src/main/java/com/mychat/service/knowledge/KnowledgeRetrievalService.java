package com.mychat.service.knowledge;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mychat.entity.dto.KnowledgeRetrieveHit;
import com.mychat.entity.dto.KnowledgeRetrieveTestRequest;
import com.mychat.entity.dto.KnowledgeRetrieveTestResponse;
import com.mychat.entity.po.DocumentMeta;
import com.mychat.entity.po.KnowledgeBase;
import com.mychat.entity.po.KnowledgeBaseSettings;
import com.mychat.mapper.DocumentMetaMapper;
import com.mychat.mapper.KnowledgeBaseMapper;
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
 * 知识库检索：召回测试、问答上下文拼装（kbScope 目录/向量），以及聊天引用装配。
 */
@Slf4j
@Service
public class KnowledgeRetrievalService {

    /**
     * 模拟问题字数上限，避免超长 embedding
     */
    public static final int MAX_QUERY_CHARS = 1000;

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final DocumentMetaMapper documentMetaMapper;
    private final VectorStore vectorStore;

    public KnowledgeRetrievalService(
            KnowledgeBaseMapper knowledgeBaseMapper,
            DocumentMetaMapper documentMetaMapper,
            VectorStore vectorStore) {
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.documentMetaMapper = documentMetaMapper;
        this.vectorStore = vectorStore;
    }

    /**
     * 聊天引用摘录上限，控制 NDJSON 体积（对齐 Dify retriever_resources.content 截短）
     */
    public static final int CITATION_SNIPPET_MAX_CHARS = 200;

    /**
     * 问答用检索上下文：prompt 给模型，citations 给 UI 来源文件名。
     *
     * @param promptBlock 注入生成侧的检索文本
     * @param catalogUsed 是否走了文档目录而非向量片段
     * @param chunkHits   向量命中条数（目录路径为 0）
     * @param citations   结构化来源（文件名等），空库为空列表
     */
    public record RagContext(
            String promptBlock,
            boolean catalogUsed,
            int chunkHits,
            List<KnowledgeRetrieveHit> citations) {
        /**
         * 空上下文占位，无引用来源。
         */
        public static RagContext empty(String message) {
            String text = StringUtils.hasText(message) ? message : "【检索上下文为空】";
            return new RagContext(text, false, 0, List.of());
        }
    }

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

        // 检索：召回测试仍走向量，便于对照分数；问答路径见 buildRagContext
        List<Document> docs = searchChunks(kbId, query, topK, threshold);
        log.info("召回测试 kbId={} topK={} threshold={} hits={}", kbId, topK, threshold, docs.size());

        // 映射 hits：优先 original，并带上 summary
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

    /**
     * 为 RAG 生成拼装好的上下文。双参默认向量检索（Routing / Demo）。
     */
    public RagContext buildRagContext(String kbId, String query) {
        return buildRagContext(kbId, query, KbScope.VECTOR);
    }

    /**
     * 按编排器给出的范围约束 kbScope 装配上下文，给接下来正式知识库检索做准备
     * CATALOG: 只拼目录；VECTOR: 检索，0 hit 仍可回退目录。
     */
    public RagContext buildRagContext(String kbId, String query, KbScope scope) {
        if (!StringUtils.hasText(kbId)) {
            return RagContext.empty("【检索上下文为空】未绑定知识库。");
        }
        String id = kbId.trim();
        String q = query == null ? "" : query.trim();
        KnowledgeBase kb = knowledgeBaseMapper.selectById(id);
        List<DocumentMeta> readyDocs = listReadyDocs(id);
        KbScope resolved = scope != null ? scope : KbScope.VECTOR;

        if (resolved == KbScope.CATALOG) {
            log.info("kbScope=catalog 走文档目录 kbId={} docs={}", id, readyDocs.size());
            return new RagContext(
                    formatCatalog(kb, readyDocs, true), true, 0, catalogCitations(readyDocs));
        }

        // 获取最相似的前几片向量，若kb已配置参数则用配置值，否则默认
        int topK = KnowledgeBaseSettings.topKOrDefault(kb != null ? kb.getTopK() : null);
        // 相似度阈值同理
        double threshold = KnowledgeBaseSettings.thresholdOrDefault(kb != null ? kb.getSimilarityThreshold() : null);
        List<Document> hits = StringUtils.hasText(q)
                ? searchChunks(id, q, topK, threshold)
                : List.of();
        if (hits.isEmpty()) {
            if (!readyDocs.isEmpty()) {
                log.info("向量 0 hit，改用文档目录 kbId={}", id);
                return new RagContext(
                        formatCatalog(kb, readyDocs, false), true, 0, catalogCitations(readyDocs));
            }
            return RagContext.empty("【检索上下文为空】该知识库尚无已就绪文档。");
        }
        return new RagContext(formatChunks(hits), false, hits.size(), chunkCitations(hits));
    }

    /**
     * 把用户问题与检索上下文拼成正式提示词
     */
    public static String wrapUserWithContext(String userText, String ragContext) {
        String question = userText != null ? userText : "";
        String ctx = StringUtils.hasText(ragContext) ? ragContext : "（无）";
        return question + "\n\n【检索上下文】\n" + ctx;
    }

    /**
     * 按知识库设置做向量检索。
     */
    List<Document> searchChunks(String kbId, String query, int topK, double threshold) {
        SearchRequest searchRequest = KbSearchRequests.filtered(kbId, topK, threshold)
                .query(query)
                .build();
        List<Document> docs = vectorStore.similaritySearch(searchRequest);
        return docs != null ? docs : List.of();
    }

    /**
     * 列出该库已就绪文档，供目录兜底。
     */
    List<DocumentMeta> listReadyDocs(String kbId) {
        LambdaQueryWrapper<DocumentMeta> q = new LambdaQueryWrapper<>();
        q.eq(DocumentMeta::getKbId, kbId)
                .eq(DocumentMeta::getStatus, DocumentMeta.STATUS_READY)
                .orderByAsc(DocumentMeta::getCreatedAt);
        List<DocumentMeta> docs = documentMetaMapper.selectList(q);
        return docs != null ? docs : List.of();
    }

    /**
     * 把 VectorStore 文档转成前端可读的命中项。
     */
    static KnowledgeRetrieveHit toHit(Document doc) {
        KnowledgeRetrieveHit hit = new KnowledgeRetrieveHit();
        Map<String, Object> meta = doc.getMetadata();
        String original = meta != null ? stringMeta(meta, ChunkSummaryService.META_ORIGINAL) : null;
        hit.setText(StringUtils.hasText(original) ? original : doc.getText());
        hit.setScore(scoreOf(doc));
        if (meta != null) {
            hit.setFilename(stringMeta(meta, "filename"));
            hit.setDocumentId(stringMeta(meta, "documentId"));
            String summary = stringMeta(meta, ChunkSummaryService.META_SUMMARY);
            hit.setSummary(StringUtils.hasText(summary) ? summary : null);
        }
        return hit;
    }

    /**
     * 向量命中转聊天引用：截断正文与摘要。
     */
    static List<KnowledgeRetrieveHit> chunkCitations(List<Document> docs) {
        if (docs == null || docs.isEmpty()) {
            return List.of();
        }
        List<KnowledgeRetrieveHit> list = new ArrayList<>(docs.size());
        for (Document doc : docs) {
            list.add(forCitation(toHit(doc), KnowledgeRetrieveHit.KIND_CHUNK));
        }
        return list;
    }

    /**
     * 就绪文档目录转聊天引用（无相似度分数）。
     */
    static List<KnowledgeRetrieveHit> catalogCitations(List<DocumentMeta> docs) {
        if (docs == null || docs.isEmpty()) {
            return List.of();
        }
        List<KnowledgeRetrieveHit> list = new ArrayList<>(docs.size());
        for (DocumentMeta doc : docs) {
            KnowledgeRetrieveHit hit = new KnowledgeRetrieveHit();
            hit.setFilename(doc.getFilename());
            hit.setDocumentId(doc.getId());
            hit.setKind(KnowledgeRetrieveHit.KIND_CATALOG);
            list.add(hit);
        }
        return list;
    }

    /**
     * 复制命中并截短摘录，供 NDJSON args.citations 使用。
     */
    static KnowledgeRetrieveHit forCitation(KnowledgeRetrieveHit hit, String kind) {
        KnowledgeRetrieveHit copy = new KnowledgeRetrieveHit();
        if (hit != null) {
            copy.setFilename(hit.getFilename());
            copy.setDocumentId(hit.getDocumentId());
            copy.setScore(hit.getScore());
            copy.setText(truncateSnippet(hit.getText()));
            copy.setSummary(truncateSnippet(hit.getSummary()));
        }
        copy.setKind(kind);
        return copy;
    }

    /**
     * 截到 {@link #CITATION_SNIPPET_MAX_CHARS}；空串视为无摘录。
     */
    static String truncateSnippet(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String t = raw.trim();
        if (t.length() <= CITATION_SNIPPET_MAX_CHARS) {
            return t;
        }
        return t.substring(0, CITATION_SNIPPET_MAX_CHARS);
    }

    /**
     * 拼文档目录。emptyHits 表示因 0 hit 而来，需提示换具体问题。
     */
    static String formatCatalog(KnowledgeBase kb, List<DocumentMeta> docs, boolean overviewIntent) {
        StringBuilder sb = new StringBuilder();
        sb.append("【文档目录｜这是知识库内已就绪文档的清单，不是检索到的全部正文。");
        sb.append("禁止根据下列条目声称「知识库仅有 N 个章节」或把目录当成全文。】\n");
        if (kb != null && StringUtils.hasText(kb.getName())) {
            sb.append("知识库：").append(kb.getName()).append('\n');
        }
        if (kb != null && StringUtils.hasText(kb.getDescription())) {
            sb.append("描述：").append(kb.getDescription()).append('\n');
        }
        if (docs == null || docs.isEmpty()) {
            sb.append("（尚无已就绪文档）\n");
            return sb.toString();
        }
        int i = 1;
        for (DocumentMeta doc : docs) {
            int chunks = doc.getChunkCount() != null ? doc.getChunkCount() : 0;
            sb.append(i++).append(". ")
                    .append(doc.getFilename() != null ? doc.getFilename() : "未命名")
                    .append("（").append(chunks).append(" 个分片）\n");
        }
        if (overviewIntent) {
            sb.append("请根据上述文档清单概括库里有哪些资料，并请用户换一个具体问题（如某文档中的概念）。\n");
        } else {
            sb.append("当前问题未检索到足够相似的正文片段。请提示用户换更具体的问题（例如 Java 多态 / 操作系统）。\n");
        }
        return sb.toString();
    }

    /**
     * 拼向量命中片段，并声明这不是全集。
     */
    static String formatChunks(List<Document> hits) {
        StringBuilder sb = new StringBuilder();
        sb.append("【检索片段｜以下只是相似度最高的若干片段，不是知识库的全部内容。禁止说「仅覆盖这些章节」。】\n");
        int i = 1;
        for (Document doc : hits) {
            Map<String, Object> meta = doc.getMetadata();
            String filename = meta != null ? stringMeta(meta, "filename") : null;
            sb.append("---\n片段 ").append(i++).append('\n');
            if (StringUtils.hasText(filename)) {
                sb.append("来源：").append(filename).append('\n');
            }
            String summary = meta != null ? stringMeta(meta, ChunkSummaryService.META_SUMMARY) : null;
            if (StringUtils.hasText(summary)) {
                sb.append("摘要：").append(summary).append('\n');
            }
            String original = meta != null ? stringMeta(meta, ChunkSummaryService.META_ORIGINAL) : null;
            String body = StringUtils.hasText(original) ? original : doc.getText();
            sb.append(body != null ? body : "").append('\n');
        }
        return sb.toString();
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
