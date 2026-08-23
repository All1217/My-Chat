package com.mychat.service.knowledge;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class EmbeddingServiceBatchTest {

    @Test
    void storeSegmentsBatchedSplitsIntoBatches() {
        List<Integer> batchSizes = new ArrayList<>();
        VectorStore store = mock(VectorStore.class);
        doAnswer(inv -> {
            List<?> batch = inv.getArgument(0);
            batchSizes.add(batch.size());
            return null;
        }).when(store).add(anyList());

        EmbeddingService svc = new EmbeddingService(store);
        int written = svc.storeSegmentsBatched(docs(70), 32);

        assertEquals(70, written);
        assertEquals(List.of(32, 32, 6), batchSizes);
    }

    @Test
    void storeSegmentsBatchedFailureReportsWrittenCount() {
        AtomicInteger calls = new AtomicInteger();
        VectorStore store = mock(VectorStore.class);
        doAnswer(inv -> {
            if (calls.incrementAndGet() == 2) {
                throw new RuntimeException("embed boom");
            }
            return null;
        }).when(store).add(anyList());

        EmbeddingService svc = new EmbeddingService(store);
        EmbeddingService.PartialEmbedException ex = assertThrows(
                EmbeddingService.PartialEmbedException.class,
                () -> svc.storeSegmentsBatched(docs(70), 32));
        assertEquals(32, ex.written());
    }

    private static List<Document> docs(int n) {
        List<Document> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(new Document("id-" + i, "text-" + i, new HashMap<>()));
        }
        return list;
    }
}
