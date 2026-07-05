package com.mychat.debug;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;

public final class AgentDebugLog {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SESSION_ID = "d859f8";

    private AgentDebugLog() {
    }

    public static void write(String hypothesisId, String location, String message, Map<String, Object> data) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("sessionId", SESSION_ID);
            payload.put("hypothesisId", hypothesisId);
            payload.put("location", location);
            payload.put("message", message);
            payload.put("data", data);
            payload.put("timestamp", System.currentTimeMillis());
            String line = MAPPER.writeValueAsString(payload) + System.lineSeparator();
            Files.writeString(resolveLogPath(), line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {
        }
    }

    public static Map<String, Object> secretStatus(String value) {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("present", value != null && !value.isBlank());
        status.put("length", value == null ? 0 : value.length());
        return status;
    }

    private static Path resolveLogPath() {
        Path cwd = Path.of(System.getProperty("user.dir"));
        if ("my-chat-server".equals(cwd.getFileName().toString())) {
            return cwd.getParent().resolve("debug-d859f8.log");
        }
        return cwd.resolve("debug-d859f8.log");
    }
}
