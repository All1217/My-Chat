package com.mychat.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot 4 默认提供 Jackson 3 JsonMapper，本项目业务代码仍使用 Jackson 2 API，
 * 因此显式注册兼容 Bean 供注入。
 */
@Configuration
public class JacksonConfiguration {

    /** 业务侧共享 ObjectMapper：忽略未知字段，避免 Schema 演进时硬失败。 */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
}
