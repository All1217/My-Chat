package com.mychat.entity.po;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KnowledgeBaseSettingsTest {

    @Test
    void validDefaultsPass() {
        assertDoesNotThrow(() -> KnowledgeBaseSettings.validate(800, 0, 5, 0.5));
    }

    @Test
    void overlapMustBeLessThanChunkSize() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> KnowledgeBaseSettings.validate(800, 800, 5, 0.5));
        assertEquals("chunkOverlap 须 ≥ 0 且小于 chunkSize", ex.getMessage());
    }

    @Test
    void topKMustBeInRange() {
        assertThrows(IllegalArgumentException.class,
                () -> KnowledgeBaseSettings.validate(800, 0, 0, 0.5));
        assertThrows(IllegalArgumentException.class,
                () -> KnowledgeBaseSettings.validate(800, 0, 21, 0.5));
    }

    @Test
    void applyDefaultsFillsNulls() {
        KnowledgeBase kb = new KnowledgeBase();
        KnowledgeBaseSettings.applyDefaults(kb);
        assertEquals(KnowledgeBaseSettings.DEFAULT_CHUNK_SIZE, kb.getChunkSize());
        assertEquals(KnowledgeBaseSettings.DEFAULT_CHUNK_OVERLAP, kb.getChunkOverlap());
        assertEquals(KnowledgeBaseSettings.DEFAULT_TOP_K, kb.getTopK());
        assertEquals(KnowledgeBaseSettings.DEFAULT_SIMILARITY_THRESHOLD, kb.getSimilarityThreshold());
    }
}
