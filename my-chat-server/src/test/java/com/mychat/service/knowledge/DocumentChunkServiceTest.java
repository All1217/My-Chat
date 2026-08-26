package com.mychat.service.knowledge;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.mychat.entity.dto.DocumentChunkListResponse;
import com.mychat.entity.po.DocumentChunk;
import com.mychat.entity.po.DocumentMeta;
import com.mychat.mapper.DocumentChunkMapper;
import com.mychat.mapper.DocumentMetaMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 切段表：覆盖写入、按 position 列表、缺文档报错。
 */
class DocumentChunkServiceTest {

    private DocumentChunkMapper documentChunkMapper;
    private DocumentMetaMapper documentMetaMapper;
    private DocumentChunkService service;

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, DocumentChunk.class);
        TableInfoHelper.initTableInfo(assistant, DocumentMeta.class);
    }

    @BeforeEach
    void setUp() {
        documentChunkMapper = mock(DocumentChunkMapper.class);
        documentMetaMapper = mock(DocumentMetaMapper.class);
        service = new DocumentChunkService(documentChunkMapper, documentMetaMapper);
    }

    /** replace 先删再按顺序插入原文与摘要。 */
    @Test
    void replaceDeletesThenInsertsInOrder() {
        Document first = segment("id-0", "原文甲", "摘要甲");
        Document second = segment("id-1", "原文乙", null);

        service.replace("doc-1", "kb-1", List.of(first, second));

        verify(documentChunkMapper).delete(any());
        ArgumentCaptor<DocumentChunk> captor = ArgumentCaptor.forClass(DocumentChunk.class);
        verify(documentChunkMapper, times(2)).insert(captor.capture());
        List<DocumentChunk> rows = captor.getAllValues();
        assertEquals(0, rows.get(0).getPosition());
        assertEquals("id-0", rows.get(0).getId());
        assertEquals("doc-1", rows.get(0).getDocumentId());
        assertEquals("kb-1", rows.get(0).getKbId());
        assertEquals("原文甲", rows.get(0).getContent());
        assertEquals("摘要甲", rows.get(0).getSummary());
        assertEquals(1, rows.get(1).getPosition());
        assertEquals("原文乙", rows.get(1).getContent());
        assertNull(rows.get(1).getSummary());
    }

    /** 无 metadata.original 时用 Document 文本。 */
    @Test
    void replaceUsesDocumentTextWhenOriginalMissing() {
        Document plain = new Document("id-0", "仅正文", new HashMap<>());

        service.replace("doc-1", "kb-1", List.of(plain));

        ArgumentCaptor<DocumentChunk> captor = ArgumentCaptor.forClass(DocumentChunk.class);
        verify(documentChunkMapper).insert(captor.capture());
        assertEquals("仅正文", captor.getValue().getContent());
    }

    /** 文档不存在时列表抛 IllegalArgumentException（接口映射 400）。 */
    @Test
    void listByDocumentIdRejectsMissingDocument() {
        when(documentMetaMapper.selectById("missing")).thenReturn(null);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> service.listByDocumentId("missing"));
        assertEquals("文档不存在", ex.getMessage());
        verify(documentChunkMapper, never()).selectList(any());
    }

    /** 列表按 mapper 返回顺序组装，无行时 chunks 为空。 */
    @Test
    void listByDocumentIdReturnsEmptyWhenNoRows() {
        DocumentMeta meta = new DocumentMeta();
        meta.setId("doc-1");
        meta.setFilename("notes.md");
        when(documentMetaMapper.selectById("doc-1")).thenReturn(meta);
        when(documentChunkMapper.selectList(any())).thenReturn(List.of());

        DocumentChunkListResponse out = service.listByDocumentId("doc-1");

        assertEquals("doc-1", out.getDocumentId());
        assertEquals("notes.md", out.getFilename());
        assertTrue(out.getChunks().isEmpty());
    }

    /** 列表项带 position / content / summary。 */
    @Test
    void listByDocumentIdMapsRowsInPositionOrder() {
        DocumentMeta meta = new DocumentMeta();
        meta.setId("doc-1");
        meta.setFilename("a.txt");
        when(documentMetaMapper.selectById("doc-1")).thenReturn(meta);
        DocumentChunk a = new DocumentChunk();
        a.setPosition(0);
        a.setContent("第一段");
        a.setSummary("摘要1");
        DocumentChunk b = new DocumentChunk();
        b.setPosition(1);
        b.setContent("第二段");
        when(documentChunkMapper.selectList(any())).thenReturn(List.of(a, b));

        DocumentChunkListResponse out = service.listByDocumentId("doc-1");

        assertEquals(2, out.getChunks().size());
        assertEquals(0, out.getChunks().get(0).getPosition());
        assertEquals("第一段", out.getChunks().get(0).getContent());
        assertEquals("摘要1", out.getChunks().get(0).getSummary());
        assertEquals(1, out.getChunks().get(1).getPosition());
        assertEquals("第二段", out.getChunks().get(1).getContent());
    }

    private static Document segment(String id, String original, String summary) {
        Map<String, Object> meta = new HashMap<>();
        meta.put(ChunkSummaryService.META_ORIGINAL, original);
        if (summary != null) {
            meta.put(ChunkSummaryService.META_SUMMARY, summary);
        }
        return new Document(id, original, meta);
    }
}
