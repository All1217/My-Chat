package com.mychat.apps.papertrade.tools;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/** 工具出入参：JSON 文本，失败收成一句说明。 */
final class PaperToolJson {

    /** 自建 Jackson 2 映射器。Spring Boot 4 只暴露 Jackson 3 的 Bean。 */
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private PaperToolJson() {
    }

    static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            return fail(e);
        }
    }

    static <T> T read(String json, Class<T> type) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("JSON 不能为空");
        }
        try {
            return MAPPER.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalArgumentException("JSON 无法解析");
        }
    }

    static String fail(Exception e) {
        String msg = e.getMessage();
        if (msg == null || msg.isBlank()) {
            msg = "操作失败";
        }
        return "{\"ok\":false,\"error\":\"" + msg.replace("\\", "\\\\").replace("\"", "'").replace("\n", " ") + "\"}";
    }
}
