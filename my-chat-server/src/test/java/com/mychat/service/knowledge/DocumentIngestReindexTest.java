package com.mychat.service.knowledge;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.mychat.config.IngestProperties;
import com.mychat.entity.po.AsyncJob;
import com.mychat.entity.po.DocumentMeta;
import com.mychat.job.AsyncJobService;
import com.mychat.mapper.AsyncJobMapper;
import com.mychat.mapper.DocumentMetaMapper;
import com.mychat.mapper.KnowledgeBaseMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InOrder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 文档级重新向量化：ingest 先删旧段；submitReindex 拒绝非法状态与缺文件。
 */
class DocumentIngestReindexTest {

    @TempDir
    Path tempDir;

    private DocumentMetaMapper documentMetaMapper;
    private AsyncJobMapper asyncJobMapper;
    private AsyncJobService asyncJobService;
    private EmbeddingService embeddingService;
    private ChunkSummaryService chunkSummaryService;
    private DocumentIngestService service;

    /** 无 Spring 时手动登记实体，供 LambdaUpdateWrapper 使用。 */
    @BeforeAll
    static void initMybatisPlusTableInfo() {
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, DocumentMeta.class);
        TableInfoHelper.initTableInfo(assistant, AsyncJob.class);
    }

    @BeforeEach
    void setUp() {
        IngestProperties ingestProperties = new IngestProperties();
        ingestProperties.setEmbedBatchSize(32);
        KnowledgeBaseMapper knowledgeBaseMapper = mock(KnowledgeBaseMapper.class);
        documentMetaMapper = mock(DocumentMetaMapper.class);
        asyncJobMapper = mock(AsyncJobMapper.class);
        asyncJobService = mock(AsyncJobService.class);
        embeddingService = mock(EmbeddingService.class);
        chunkSummaryService = mock(ChunkSummaryService.class);
        when(chunkSummaryService.enrich(anyList())).thenAnswer(inv -> inv.getArgument(0));
        service = new DocumentIngestService(
                ingestProperties,
                knowledgeBaseMapper,
                documentMetaMapper,
                asyncJobMapper,
                asyncJobService,
                new DocumentService(),
                embeddingService,
                chunkSummaryService);
        when(knowledgeBaseMapper.selectById(any())).thenReturn(null);
        when(embeddingService.storeSegmentsBatched(anyList(), anyInt())).thenAnswer(inv -> {
            List<?> segments = inv.getArgument(0);
            return segments.size();
        });
    }

    /** ingest 须按旧 chunkCount 删向量，再写入新段。 */
    @Test
    void ingestDeletesOldSegmentsBeforeStoring() throws Exception {
        Path file = tempDir.resolve("a.txt");
        Files.writeString(file, "知识库入库测试段落。".repeat(80));
        DocumentMeta meta = readyMeta("doc-1", file);
        meta.setChunkCount(5);
        when(documentMetaMapper.selectById("doc-1")).thenReturn(meta);

        service.ingest("doc-1");

        InOrder order = inOrder(embeddingService);
        order.verify(embeddingService).deleteByDocumentId("doc-1", 5);
        order.verify(embeddingService).storeSegmentsBatched(anyList(), eq(32));
        verify(chunkSummaryService).enrich(anyList());
    }

    /** PROCESSING 文档不可再提交重跑。 */
    @Test
    void submitReindexRejectsProcessing() {
        DocumentMeta meta = new DocumentMeta();
        meta.setId("doc-1");
        meta.setStatus(DocumentMeta.STATUS_PROCESSING);
        meta.setStoragePath(tempDir.resolve("missing.txt").toString());
        when(documentMetaMapper.selectById("doc-1")).thenReturn(meta);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> service.submitReindex("doc-1"));
        assertEquals("文档正在处理中", ex.getMessage());
        verify(asyncJobService, never()).submit(any(), any(), any(), any());
    }

    /** 落盘文件不存在时提示重新上传。 */
    @Test
    void submitReindexRejectsMissingFile() {
        DocumentMeta meta = readyMeta("doc-1", tempDir.resolve("gone.txt"));
        when(documentMetaMapper.selectById("doc-1")).thenReturn(meta);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> service.submitReindex("doc-1"));
        assertEquals("落盘文件丢失，请重新上传", ex.getMessage());
        verify(asyncJobService, never()).submit(any(), any(), any(), any());
    }

    /** 合法 READY 文档提交 kb_ingest，标题为重新向量化。 */
    @Test
    void submitReindexSubmitsKbIngestJob() throws Exception {
        Path file = tempDir.resolve("notes.md");
        Files.writeString(file, "hello");
        DocumentMeta meta = readyMeta("doc-1", file);
        when(documentMetaMapper.selectById("doc-1")).thenReturn(meta);
        when(asyncJobMapper.selectCount(any())).thenReturn(0L);

        service.submitReindex("doc-1");

        verify(asyncJobService).submit(
                eq(DocumentIngestService.JOB_TYPE),
                eq("重新向量化：notes.md"),
                eq("doc-1"),
                any());
    }

    private static DocumentMeta readyMeta(String id, Path file) {
        DocumentMeta meta = new DocumentMeta();
        meta.setId(id);
        meta.setKbId("kb-1");
        meta.setFilename(file.getFileName().toString());
        meta.setStatus(DocumentMeta.STATUS_READY);
        meta.setChunkCount(3);
        meta.setStoragePath(file.toString());
        return meta;
    }
}
