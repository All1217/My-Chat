package com.mychat.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * 进程内 Caffeine 缓存；单实例部署足够，刻意不上 Redis 以节省 2C4G 内存。
 */
@Configuration
@EnableCaching
public class CacheConfiguration {

    /** 大模型目录列表缓存名，与 {@code @Cacheable} 对齐。 */
    public static final String LLM_MODELS = "llmModels";

    /** 注册 llmModels：最多 64 条、写入后 5 分钟过期。 */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(LLM_MODELS);
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(64)
                .expireAfterWrite(5, TimeUnit.MINUTES));
        return manager;
    }
}
