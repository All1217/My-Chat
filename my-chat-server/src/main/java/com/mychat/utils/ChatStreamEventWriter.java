package com.mychat.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mychat.common.ChatStreamEvent;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 将 {@link ChatStreamEvent} 序列化为 NDJSON 行（末尾带 {@code \n}）。
 * <p>
 * Spring Boot 4 自动配置的是 Jackson 3（{@code tools.jackson}），不再提供
 * {@code com.fasterxml.jackson.databind.ObjectMapper} Bean；此处自建 Jackson 2 Mapper，
 * 与项目中 {@code @JsonInclude} / 实体注解所用的 {@code com.fasterxml} 包一致。
 */
@Slf4j
@Component
public class ChatStreamEventWriter {

    @Getter
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * @return 一行 NDJSON，失败时返回 error 事件行（尽量不中断流）
     */
    public String toLine(ChatStreamEvent event) {
        try {
            return objectMapper.writeValueAsString(event) + "\n";
        } catch (JsonProcessingException e) {
            log.warn("ChatStreamEvent 序列化失败: {}", e.getMessage());
            try {
                ChatStreamEvent fallback = ChatStreamEvent.error(
                        event != null ? event.turnId() : "unknown",
                        new java.util.concurrent.atomic.AtomicInteger(0),
                        "event serialize failed");
                return objectMapper.writeValueAsString(fallback) + "\n";
            } catch (JsonProcessingException ex) {
                return "{\"v\":1,\"type\":\"error\",\"message\":\"event serialize failed\"}\n";
            }
        }
    }
}
