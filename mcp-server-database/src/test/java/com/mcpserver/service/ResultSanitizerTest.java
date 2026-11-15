package com.mcpserver.service;

import com.mcpserver.config.DatabaseMcpProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 敏感列脱敏与单元格截断。 */
class ResultSanitizerTest {

    private ResultSanitizer sanitizer;

    @BeforeEach
    void setUp() {
        DatabaseMcpProperties props = new DatabaseMcpProperties();
        props.setMaxCellLength(10);
        sanitizer = new ResultSanitizer(props);
    }

    @Test
    void masksApiKeyColumn() {
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("api_key", "sk-abcdefghijklmnop");
        row.put("name", "DeepSeek");
        rows.add(row);

        Map<String, Object> meta = sanitizer.sanitize(rows);
        assertTrue((Boolean) meta.get("sensitiveMasked"));
        assertEquals(1, meta.get("maskedCells"));
        assertEquals("sk-****mnop", rows.get(0).get("api_key"));
        assertEquals("DeepSeek", rows.get(0).get("name"));
    }

    @Test
    void truncatesLongStringCells() {
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("content", "0123456789ABCDEF");
        rows.add(row);

        Map<String, Object> meta = sanitizer.sanitize(rows);
        assertTrue((Boolean) meta.get("truncated"));
        assertEquals(1, meta.get("truncatedCells"));
        assertEquals("0123456789…", rows.get(0).get("content"));
    }

    @Test
    void sensitiveDetectionIsCaseInsensitive() {
        assertTrue(ResultSanitizer.isSensitive("API_KEY", List.of("api_key")));
        assertTrue(ResultSanitizer.isSensitive("userPassword", List.of("password")));
        assertFalse(ResultSanitizer.isSensitive("model_id", List.of("api_key")));
    }

    @Test
    void shortSecretFullyStars() {
        assertEquals("****", ResultSanitizer.maskValue("short"));
    }
}
