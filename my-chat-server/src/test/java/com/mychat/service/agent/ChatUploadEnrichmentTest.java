package com.mychat.service.agent;

import com.mychat.service.knowledge.DocumentService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 主聊天附件基线：正文只进本轮 agentInput，Memory 只留文件名和原问。 */
class ChatUploadEnrichmentTest {

    /** 附件正文与 Memory 文本保持现有分流语义。 */
    @Test
    void documentBodyOnlyEnrichesAgentInput() throws Exception {
        DocumentService documents = mock(DocumentService.class);
        when(documents.extractPlainText(any(InputStream.class), eq("note.txt")))
                .thenReturn("附件中的关键事实");
        ChatUploadEnrichment enrichment = new ChatUploadEnrichment(documents);
        MockMultipartFile file = new MockMultipartFile(
                "files",
                "note.txt",
                "text/plain",
                "raw".getBytes(StandardCharsets.UTF_8));

        String agentInput = enrichment.enrichPromptWithUploadedDocuments(
                "请总结", List.of(file));
        String memoryText = enrichment.buildMemoryUserText(
                "请总结", List.of(file));

        assertTrue(agentInput.contains("note.txt"));
        assertTrue(agentInput.contains("附件中的关键事实"));
        assertTrue(agentInput.endsWith("用户的问题：\n请总结"));
        assertTrue(memoryText.contains("note.txt"));
        assertTrue(memoryText.contains("请总结"));
        assertFalse(memoryText.contains("附件中的关键事实"));
    }
}
