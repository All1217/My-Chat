package com.mychat.service.knowledge;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DocumentServiceProcessIdTest {

    @Test
    void specifiedDocumentIdProducesStableSegmentIds() {
        DocumentService svc = new DocumentService();
        String documentId = "doc-fixed-id";
        String text = "知识库入库测试段落。".repeat(80);
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);

        var first = svc.processDocument(new ByteArrayInputStream(bytes), "a.txt", "kb1", documentId);
        var second = svc.processDocument(new ByteArrayInputStream(bytes), "a.txt", "kb1", documentId);

        assertEquals(documentId, first.documentId());
        assertEquals(documentId, second.documentId());
        assertFalse(first.segments().isEmpty());
        assertEquals(first.segments().get(0).getId(), second.segments().get(0).getId());
        assertEquals(
                EmbeddingService.buildSegmentIds(documentId, first.segments().size()),
                first.segments().stream().map(org.springframework.ai.document.Document::getId).toList());
    }
}
