package com.mychat.service;

import com.mychat.entity.po.DocumentMeta;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class EmbeddingService {
    @Autowired
    private VectorStore vectorStore;

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

    public void deleteByDocumentId(String documentId, int chunkCount) {
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
            allIds.addAll(buildSegmentIds(doc.getId(), doc.getChunkCount()));
        }
        log.info("Deleting {} segments across {} documents", allIds.size(), docs.size());
        try {
            vectorStore.delete(allIds);
        } catch (Exception e) {
            log.error("Failed to delete segments", e);
        }
    }

    private static List<String> buildSegmentIds(String documentId, int chunkCount) {
        List<String> ids = new ArrayList<>(chunkCount);
        for (int i = 0; i < chunkCount; i++) {
            ids.add(UUID.nameUUIDFromBytes(
                    (documentId + "_" + i).getBytes(StandardCharsets.UTF_8)).toString());
        }
        return ids;
    }
}
