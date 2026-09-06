package com.mychat.service.agent.pipeline;

import com.mychat.common.ChatStreamEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Memory 写路径：只拼 text_delta，保留空白，空正文不落库。
 */
class OrchestrateTurnPersistenceTest {

    @Test
    void persistConcatenatesTextDeltasIncludingWhitespace() {
        ChatMemory memory = mock(ChatMemory.class);
        OrchestrateTurnPersistence persistence = new OrchestrateTurnPersistence(memory);
        AtomicInteger seq = new AtomicInteger(0);
        List<ChatStreamEvent> events = List.of(
                ChatStreamEvent.route("t", seq, "orchestrate", "r"),
                ChatStreamEvent.textDelta("t", seq, "## "),
                ChatStreamEvent.textDelta("t", seq, "标题"),
                ChatStreamEvent.textDelta("t", seq, "\n"));

        persistence.persistOrchestrateExchange("chat-1", "用户问", events);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Message>> captor = ArgumentCaptor.forClass(List.class);
        verify(memory).add(eq("chat-1"), captor.capture());
        List<Message> saved = captor.getValue();
        assertEquals(2, saved.size());
        assertEquals("用户问", saved.get(0).getText());
        assertEquals("## 标题\n", saved.get(1).getText());
    }

    @Test
    void persistSkipsWhenNoAssistantText() {
        ChatMemory memory = mock(ChatMemory.class);
        OrchestrateTurnPersistence persistence = new OrchestrateTurnPersistence(memory);
        persistence.persistOrchestrateExchange("chat-1", "用户问", List.of());
        verify(memory, never()).add(eq("chat-1"), org.mockito.ArgumentMatchers.<List<Message>>any());
    }
}
