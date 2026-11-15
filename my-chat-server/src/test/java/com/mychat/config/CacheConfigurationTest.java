package com.mychat.config;

import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Caffeine CacheManager 注册 llmModels 并可读写。 */
class CacheConfigurationTest {

    @Test
    void llmModelsCacheRoundTrip() {
        CacheManager manager = new CacheConfiguration().cacheManager();
        Cache cache = manager.getCache(CacheConfiguration.LLM_MODELS);
        assertNotNull(cache);

        cache.put("list", List.of("a", "b"));
        assertEquals(List.of("a", "b"), cache.get("list", List.class));

        cache.clear();
        assertNull(cache.get("list"));
    }
}
