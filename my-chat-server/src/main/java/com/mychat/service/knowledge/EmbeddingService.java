package com.mychat.service.knowledge;

import com.mychat.entity.po.DocumentMeta;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmbeddingService {
    private final VectorStore vectorStore;

    public int storeSegments(List<Document> segments) {
        log.info("Storing {} segments in vector store", segments.size());
        try {
            vectorStore.add(segments);
            log.info("Successfully stored {} segments", segments.size());
            return segments.size();
        } catch (Exception e) {
            log.error("Failed to store segments", e);
            throw new RuntimeException("Segment storage failed: " + e.getMessage(), e);
        }
    }

    /**
     * 按批写入向量。失败时 {@link PartialEmbedException#written()} 为已成功写入的段数，便于回滚。
     */
    public int storeSegmentsBatched(List<Document> segments, int batchSize) {
        if (segments == null || segments.isEmpty()) {
            return 0;
        }
        int size = Math.max(1, batchSize);
        int written = 0;
        try {
            for (int i = 0; i < segments.size(); i += size) {
                List<Document> batch = segments.subList(i, Math.min(i + size, segments.size()));
                vectorStore.add(batch);
                written += batch.size();
                log.info("Stored embedding batch {}-{} / {}", i, written, segments.size());
            }
            return written;
        } catch (Exception e) {
            log.error("Failed to store segments after {} written", written, e);
            throw new PartialEmbedException(written, e);
        }
    }

    public void deleteByDocumentId(String documentId, int chunkCount) {
        if (documentId == null || chunkCount <= 0) {
            return;
        }
        List<String> segmentIds = buildSegmentIds(documentId, chunkCount);
        log.info("Deleting {} segments for document: {}", chunkCount, documentId);
        try {
            vectorStore.delete(segmentIds);
        } catch (Exception e) {
            log.error("Failed to delete segments for document: {}", documentId, e);
        }
    }

    public void deleteByDocumentMetas(List<DocumentMeta> docs) {
        List<String> allIds = new ArrayList<>();
        for (DocumentMeta doc : docs) {
            int n = doc.getChunkCount() != null ? doc.getChunkCount() : 0;
            allIds.addAll(buildSegmentIds(doc.getId(), n));
        }
        if (allIds.isEmpty()) {
            return;
        }
        log.info("Deleting {} segments across {} documents", allIds.size(), docs.size());
        try {
            vectorStore.delete(allIds);
        } catch (Exception e) {
            log.error("Failed to delete segments", e);
        }
    }

    public static List<String> buildSegmentIds(String documentId, int chunkCount) {
        List<String> ids = new ArrayList<>(Math.max(chunkCount, 0));
        for (int i = 0; i < chunkCount; i++) {
            ids.add(UUID.nameUUIDFromBytes(
                    (documentId + "_" + i).getBytes(StandardCharsets.UTF_8)).toString());
        }
        return ids;
    }

    /** 分批写入中途失败：已写入段数供调用方按 documentId 删除。 */
    public static final class PartialEmbedException extends RuntimeException {
        private final int written;

        public PartialEmbedException(int written, Throwable cause) {
            super(cause.getMessage(), cause);
            this.written = written;
        }

        public int written() {
            return written;
        }
    }
}
