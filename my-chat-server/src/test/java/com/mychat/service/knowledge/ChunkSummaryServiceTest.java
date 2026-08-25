package com.mychat.service.knowledge;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * chunk 摘要拼装与失败降级。
 */
class ChunkSummaryServiceTest {

    private ChatClient chatClient;
    private ChunkSummaryService service;

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        service = new ChunkSummaryService(chatClient);
    }

    /** 摘要与原文按固定标记拼接。 */
    @Test
    void mergeContentUsesFixedMarkers() {
        String merged = ChunkSummaryService.mergeContent("本节讲封装。", "隐藏实现细节。");
        assertTrue(merged.startsWith("【摘要】"));
        assertTrue(merged.contains("本节讲封装。"));
        assertTrue(merged.contains("【原文】"));
        assertTrue(merged.contains("隐藏实现细节。"));
    }

    /** 无摘要时 content 就是原文。 */
    @Test
    void mergeContentWithoutSummaryKeepsOriginal() {
        assertEquals("原文", ChunkSummaryService.mergeContent("  ", "原文"));
        assertEquals("原文", ChunkSummaryService.mergeContent(null, "原文"));
    }

    /** 模型返回摘要时写入 metadata 并拼进 content。 */
    @Test
    void enrichWritesSummaryAndOriginal() {
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn("本节介绍 Java 封装。");
        Map<String, Object> meta = new HashMap<>();
        meta.put("filename", "java.md");
        meta.put("documentId", "doc-1");
        meta.put("kbId", "kb-1");
        Document seg = new Document("seg-1", "封装把细节藏起来。", meta);

        List<Document> out = service.enrich(List.of(seg));

        assertEquals(1, out.size());
        Document enriched = out.get(0);
        assertEquals("seg-1", enriched.getId());
        assertTrue(enriched.getText().contains("【摘要】"));
        assertTrue(enriched.getText().contains("本节介绍 Java 封装。"));
        assertTrue(enriched.getText().contains("【原文】"));
        assertEquals("本节介绍 Java 封装。", enriched.getMetadata().get("summary"));
        assertEquals("封装把细节藏起来。", enriched.getMetadata().get("original"));
        assertEquals("java.md", enriched.getMetadata().get("filename"));
    }

    /** LLM 抛错时该块降级为原文，不写 summary。 */
    @Test
    void enrichDegradesToOriginalWhenLlmFails() {
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenThrow(new RuntimeException("timeout"));
        Document seg = new Document("seg-1", "进程与线程。", Map.of("kbId", "kb-1"));

        List<Document> out = service.enrich(List.of(seg));

        assertEquals(1, out.size());
        Document degraded = out.get(0);
        assertEquals("进程与线程。", degraded.getText());
        assertFalse(degraded.getMetadata().containsKey("summary"));
        assertEquals("进程与线程。", degraded.getMetadata().get("original"));
    }
}
