package com.mychat.service.knowledge;

import com.mychat.entity.dto.KnowledgeRetrieveTestRequest;
import com.mychat.entity.dto.KnowledgeRetrieveTestResponse;
import com.mychat.entity.po.KnowledgeBase;
import com.mychat.mapper.KnowledgeBaseMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 召回测试：校验参数、kbId 过滤，以及临时覆盖 topK/阈值。
 */
class KnowledgeRetrievalServiceTest {

    private KnowledgeBaseMapper knowledgeBaseMapper;
    private VectorStore vectorStore;
    private KnowledgeRetrievalService service;

    @BeforeEach
    void setUp() {
        knowledgeBaseMapper = mock(KnowledgeBaseMapper.class);
        vectorStore = mock(VectorStore.class);
        service = new KnowledgeRetrievalService(knowledgeBaseMapper, vectorStore);
        KnowledgeBase kb = new KnowledgeBase();
        kb.setId("kb-1");
        kb.setTopK(5);
        kb.setSimilarityThreshold(0.5);
        when(knowledgeBaseMapper.selectById("kb-1")).thenReturn(kb);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
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

    /** 命中片段映射文件名与分数。 */
    @Test
    void retrieveTestMapsHits() {
        Map<String, Object> meta = new HashMap<>();
        meta.put("filename", "Java基础.md");
        meta.put("documentId", "doc-1");
        meta.put("score", 0.91);
        Document doc = new Document("seg-1", "封装把细节藏起来", meta);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc));

        KnowledgeRetrieveTestRequest req = new KnowledgeRetrieveTestRequest();
        req.setKbId("kb-1");
        req.setQuery("封装");
        KnowledgeRetrieveTestResponse resp = service.retrieveTest(req);

        assertEquals(1, resp.getHits().size());
        assertEquals("封装把细节藏起来", resp.getHits().get(0).getText());
        assertEquals("Java基础.md", resp.getHits().get(0).getFilename());
        assertEquals("doc-1", resp.getHits().get(0).getDocumentId());
        assertEquals(0.91, resp.getHits().get(0).getScore());
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

    private SearchRequest captureSearch() {
        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        return captor.getValue();
    }
}
