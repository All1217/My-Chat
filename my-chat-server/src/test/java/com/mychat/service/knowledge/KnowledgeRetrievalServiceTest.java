package com.mychat.service.knowledge;

import com.mychat.entity.dto.KnowledgeRetrieveHit;
import com.mychat.entity.dto.KnowledgeRetrieveTestRequest;
import com.mychat.entity.dto.KnowledgeRetrieveTestResponse;
import com.mychat.entity.po.DocumentMeta;
import com.mychat.entity.po.KnowledgeBase;
import com.mychat.mapper.DocumentMetaMapper;
import com.mychat.mapper.KnowledgeBaseMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 召回测试参数、hit 映射，以及总览问走目录。
 */
class KnowledgeRetrievalServiceTest {

    private KnowledgeBaseMapper knowledgeBaseMapper;
    private DocumentMetaMapper documentMetaMapper;
    private VectorStore vectorStore;
    private KnowledgeRetrievalService service;

    /** LambdaQueryWrapper 需要登记 DocumentMeta 表信息。 */
    @BeforeAll
    static void initMybatisPlusTableInfo() {
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, DocumentMeta.class);
    }

    @BeforeEach
    void setUp() {
        knowledgeBaseMapper = mock(KnowledgeBaseMapper.class);
        documentMetaMapper = mock(DocumentMetaMapper.class);
        vectorStore = mock(VectorStore.class);
        service = new KnowledgeRetrievalService(knowledgeBaseMapper, documentMetaMapper, vectorStore);
        KnowledgeBase kb = new KnowledgeBase();
        kb.setId("kb-1");
        kb.setName("面试资料");
        kb.setTopK(5);
        kb.setSimilarityThreshold(0.5);
        when(knowledgeBaseMapper.selectById("kb-1")).thenReturn(kb);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        when(documentMetaMapper.selectList(any())).thenReturn(List.of());
    }

    /** 默认使用知识库已存 topK / 阈值，并带 kbId 过滤。 */
    @Test
    void retrieveTestUsesKbSettingsAndFilter() {
        KnowledgeRetrieveTestRequest req = new KnowledgeRetrieveTestRequest();
        req.setKbId("kb-1");
        req.setQuery("Java 三大特性");

        service.retrieveTest(req);

        SearchRequest captured = captureSearch();
        assertEquals("Java 三大特性", captured.getQuery());
        assertEquals(5, captured.getTopK());
        assertEquals(0.5, captured.getSimilarityThreshold());
        assertTrue(String.valueOf(captured.getFilterExpression()).contains("kb-1"));
    }

    /** 请求里的 topK / 阈值只作用于本次检索。 */
    @Test
    void retrieveTestOverridesTopKAndThreshold() {
        KnowledgeRetrieveTestRequest req = new KnowledgeRetrieveTestRequest();
        req.setKbId("kb-1");
        req.setQuery("封装");
        req.setTopK(3);
        req.setSimilarityThreshold(0.2);

        service.retrieveTest(req);

        SearchRequest captured = captureSearch();
        assertEquals(3, captured.getTopK());
        assertEquals(0.2, captured.getSimilarityThreshold());
        verify(knowledgeBaseMapper).selectById("kb-1");
    }

    /** 命中片段映射文件名与分数；有 original/summary 时分开返回。 */
    @Test
    void retrieveTestMapsHitsPreferOriginalAndSummary() {
        Map<String, Object> meta = new HashMap<>();
        meta.put("filename", "Java基础.md");
        meta.put("documentId", "doc-1");
        meta.put("score", 0.91);
        meta.put("summary", "本节讲封装。");
        meta.put("original", "封装把细节藏起来");
        Document doc = new Document("seg-1", "【摘要】\n本节讲封装。\n\n【原文】\n封装把细节藏起来", meta);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc));

        KnowledgeRetrieveTestRequest req = new KnowledgeRetrieveTestRequest();
        req.setKbId("kb-1");
        req.setQuery("封装");
        KnowledgeRetrieveTestResponse resp = service.retrieveTest(req);

        KnowledgeRetrieveHit hit = resp.getHits().get(0);
        assertEquals("封装把细节藏起来", hit.getText());
        assertEquals("本节讲封装。", hit.getSummary());
        assertEquals("Java基础.md", hit.getFilename());
        assertEquals("doc-1", hit.getDocumentId());
        assertEquals(0.91, hit.getScore());
    }

    @Test
    void retrieveTestRejectsEmptyQuery() {
        KnowledgeRetrieveTestRequest req = new KnowledgeRetrieveTestRequest();
        req.setKbId("kb-1");
        req.setQuery("   ");
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> service.retrieveTest(req));
        assertEquals("query 不能为空", ex.getMessage());
    }

    @Test
    void retrieveTestRejectsMissingKb() {
        KnowledgeRetrieveTestRequest req = new KnowledgeRetrieveTestRequest();
        req.setKbId("missing");
        req.setQuery("hello");
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> service.retrieveTest(req));
        assertEquals("知识库不存在", ex.getMessage());
    }

    /** 总览问不走向量，只注入目录。 */
    @Test
    void overviewQueryUsesCatalogNotVector() {
        DocumentMeta doc = new DocumentMeta();
        doc.setFilename("Java基础.md");
        doc.setChunkCount(12);
        doc.setStatus(DocumentMeta.STATUS_READY);
        when(documentMetaMapper.selectList(any())).thenReturn(List.of(doc));

        KnowledgeRetrievalService.RagContext ctx =
                service.buildRagContext("kb-1", "这个知识库总体而言讲了些什么？");

        assertTrue(ctx.catalogUsed());
        assertEquals(0, ctx.chunkHits());
        assertTrue(ctx.promptBlock().contains("文档目录"));
        assertTrue(ctx.promptBlock().contains("Java基础.md"));
        assertTrue(ctx.promptBlock().contains("禁止根据下列条目声称"));
        verify(vectorStore, never()).similaritySearch(any(SearchRequest.class));
    }

    /** 具体问 0 hit 且有就绪文档时改用目录。 */
    @Test
    void emptyHitsFallBackToCatalog() {
        DocumentMeta doc = new DocumentMeta();
        doc.setFilename("计算机基础.pdf");
        doc.setChunkCount(8);
        when(documentMetaMapper.selectList(any())).thenReturn(List.of(doc));

        KnowledgeRetrievalService.RagContext ctx = service.buildRagContext("kb-1", "量子纠缠");

        assertTrue(ctx.catalogUsed());
        assertTrue(ctx.promptBlock().contains("计算机基础.pdf"));
        assertTrue(ctx.promptBlock().contains("未检索到足够相似"));
        verify(vectorStore).similaritySearch(any(SearchRequest.class));
    }

    @Test
    void isOverviewQueryDetectsMetaQuestions() {
        assertTrue(KnowledgeRetrievalService.isOverviewQuery("这个知识库总体而言是关于什么内容的？"));
        assertTrue(KnowledgeRetrievalService.isOverviewQuery("库里有哪些文档"));
        assertFalse(KnowledgeRetrievalService.isOverviewQuery("Java 三大特性是什么"));
        assertFalse(KnowledgeRetrievalService.isOverviewQuery("这个知识库里的多态怎么实现"));
    }

    private SearchRequest captureSearch() {
        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        return captor.getValue();
    }
}
