package com.mychat.service.agent.pipeline;

import com.mychat.service.chat.ChatAssistantTurnService;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.SignalType;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * 收尾语义：Memory 仅 COMPLETE；turns 在 complete/cancel/error 都异步写。
 */
class ChatTurnFinalizerTest {

    @Test
    void completePersistsMemoryAndSavesTurn() {
        OrchestrateTurnPersistence persistence = mock(OrchestrateTurnPersistence.class);
        ChatAssistantTurnService turns = mock(ChatAssistantTurnService.class);
        ChatTurnFinalizer finalizer = new ChatTurnFinalizer(persistence, turns);
        ChatTurnContext ctx = ChatTurnContext.open("in", "mem", "p", "chat-1", null, false, null);

        finalizer.finalizeTurn(ctx, SignalType.ON_COMPLETE);

        verify(persistence).persist(ctx);
        verify(turns, timeout(1000)).saveTurnFromEvents(
                eq("chat-1"), eq(ctx.getTurnId()), anyList(), eq(false));
    }

    @Test
    void cancelDoesNotPersistMemoryButStillSavesTurn() {
        OrchestrateTurnPersistence persistence = mock(OrchestrateTurnPersistence.class);
        ChatAssistantTurnService turns = mock(ChatAssistantTurnService.class);
        ChatTurnFinalizer finalizer = new ChatTurnFinalizer(persistence, turns);
        ChatTurnContext ctx = ChatTurnContext.open("in", "mem", "p", "chat-1", null, false, null);

        finalizer.finalizeTurn(ctx, SignalType.CANCEL);

        verify(persistence, never()).persist(any());
        verify(turns, timeout(1000)).saveTurnFromEvents(
                anyString(), anyString(), anyList(), eq(true));
    }

    @Test
    void errorDoesNotPersistMemoryButStillSavesTurn() {
        OrchestrateTurnPersistence persistence = mock(OrchestrateTurnPersistence.class);
        ChatAssistantTurnService turns = mock(ChatAssistantTurnService.class);
        ChatTurnFinalizer finalizer = new ChatTurnFinalizer(persistence, turns);
        ChatTurnContext ctx = ChatTurnContext.open("in", "mem", "p", "chat-1", null, false, null);

        finalizer.finalizeTurn(ctx, SignalType.ON_ERROR);

        verify(persistence, never()).persist(any());
        verify(turns, timeout(1000)).saveTurnFromEvents(
                anyString(), anyString(), anyList(), anyBoolean());
    }
}
