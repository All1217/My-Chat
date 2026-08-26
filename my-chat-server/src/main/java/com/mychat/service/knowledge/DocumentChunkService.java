package com.mychat.service.knowledge;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mychat.entity.dto.DocumentChunkItem;
import com.mychat.entity.dto.DocumentChunkListResponse;
import com.mychat.entity.po.DocumentChunk;
import com.mychat.entity.po.DocumentMeta;
import com.mychat.mapper.DocumentChunkMapper;
import com.mychat.mapper.DocumentMetaMapper;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 文档切段表：入库双写、删文档清理、只读列表。不参与向量检索。
 */
@Service
public class DocumentChunkService {

    private final DocumentChunkMapper documentChunkMapper;
    private final DocumentMetaMapper documentMetaMapper;

    public DocumentChunkService(DocumentChunkMapper documentChunkMapper, DocumentMetaMapper documentMetaMapper) {
        this.documentChunkMapper = documentChunkMapper;
        this.documentMetaMapper = documentMetaMapper;
    }

    /**
     * 覆盖写入某文档的切段：先删旧行，再按列表顺序插入。
     */
    @Transactional(rollbackFor = Exception.class)
    public void replace(String documentId, String kbId, List<Document> segments) {
        if (!StringUtils.hasText(documentId)) {
            throw new IllegalArgumentException("documentId 不能为空");
        }
        deleteByDocumentId(documentId);
        if (segments == null || segments.isEmpty()) {
            return;
        }
        for (int i = 0; i < segments.size(); i++) {
            Document segment = segments.get(i);
            DocumentChunk row = new DocumentChunk();
            row.setId(segmentIdOf(documentId, i, segment));
            row.setDocumentId(documentId);
            row.setKbId(kbId);
            row.setPosition(i);
            row.setContent(originalOf(segment));
            row.setSummary(summaryOf(segment));
            documentChunkMapper.insert(row);
        }
    }

    /**
     * 按文档删除全部切段（删文档 / 入库失败回滚 / 重跑前清理）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteByDocumentId(String documentId) {
        if (!StringUtils.hasText(documentId)) {
            return;
        }
        LambdaQueryWrapper<DocumentChunk> q = new LambdaQueryWrapper<>();
        q.eq(DocumentChunk::getDocumentId, documentId);
        documentChunkMapper.delete(q);
    }

    /**
     * 按 position 升序返回切段；文档不存在抛 400；无行返回空列表。
     */
    public DocumentChunkListResponse listByDocumentId(String documentId) {
        if (!StringUtils.hasText(documentId)) {
            throw new IllegalArgumentException("文档不存在");
        }
        DocumentMeta meta = documentMetaMapper.selectById(documentId.trim());
        if (meta == null) {
            throw new IllegalArgumentException("文档不存在");
        }
        LambdaQueryWrapper<DocumentChunk> q = new LambdaQueryWrapper<>();
        q.eq(DocumentChunk::getDocumentId, meta.getId())
                .orderByAsc(DocumentChunk::getPosition);
        List<DocumentChunk> rows = documentChunkMapper.selectList(q);

        DocumentChunkListResponse out = new DocumentChunkListResponse();
        out.setDocumentId(meta.getId());
        out.setFilename(meta.getFilename());
        for (DocumentChunk row : rows) {
            DocumentChunkItem item = new DocumentChunkItem();
            item.setPosition(row.getPosition() != null ? row.getPosition() : 0);
            item.setContent(row.getContent());
            item.setSummary(row.getSummary());
            out.getChunks().add(item);
        }
        return out;
    }

    /**
     * 优先用向量段已有 ID，缺则与切段下标生成同一套 nameUUID。
     */
    static String segmentIdOf(String documentId, int index, Document segment) {
        if (segment != null && StringUtils.hasText(segment.getId())) {
            return segment.getId();
        }
        return UUID.nameUUIDFromBytes((documentId + "_" + index).getBytes(StandardCharsets.UTF_8)).toString();
    }

    /**
     * 取切段原文：优先 metadata.original，否则 Document 文本。
     */
    static String originalOf(Document segment) {
        if (segment == null) {
            return "";
        }
        String fromMeta = metaString(segment, ChunkSummaryService.META_ORIGINAL);
        if (StringUtils.hasText(fromMeta)) {
            return fromMeta;
        }
        return segment.getText() != null ? segment.getText() : "";
    }

    /**
     * 取 chunk 摘要；无则空。
     */
    static String summaryOf(Document segment) {
        String summary = metaString(segment, ChunkSummaryService.META_SUMMARY);
        return StringUtils.hasText(summary) ? summary : null;
    }

    private static String metaString(Document segment, String key) {
        Map<String, Object> meta = segment.getMetadata();
        if (meta == null) {
            return null;
        }
        Object value = meta.get(key);
        return value != null ? value.toString() : null;
    }
}
