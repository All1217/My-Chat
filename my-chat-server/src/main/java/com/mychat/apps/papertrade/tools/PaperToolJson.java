package com.mychat.apps.papertrade.tools;

import com.fasterxml.jackson.databind.ObjectMapper;

/** 工具出入参：JSON 文本，失败收成一句说明。 */
final class PaperToolJson {

    private PaperToolJson() {
    }

    static String write(ObjectMapper mapper, Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            return fail(e);
        }
    }

    static <T> T read(ObjectMapper mapper, String json, Class<T> type) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("JSON 不能为空");
        }
        try {
            return mapper.readValue(json, type);
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
