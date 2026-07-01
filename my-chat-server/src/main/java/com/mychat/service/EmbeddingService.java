package com.mychat.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

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
}
