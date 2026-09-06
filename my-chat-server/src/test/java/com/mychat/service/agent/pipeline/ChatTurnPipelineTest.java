package com.mychat.service.agent.pipeline;

import com.mychat.service.agent.pipeline.stage.EmitRouteStage;
import com.mychat.service.agent.pipeline.stage.LoadDialogueContextStage;
import com.mychat.service.agent.pipeline.stage.OrchestrateStage;
import com.mychat.service.agent.pipeline.stage.QualityLoopStage;
import com.mychat.service.agent.pipeline.stage.StreamFinalAnswerStage;
import com.mychat.utils.ChatStreamEventWriter;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 管道注册顺序与「插一个空 Stage」扩展点（不依赖 Spring 上下文）。
 */
class ChatTurnPipelineTest {

    @Test
    void defaultStageOrderIsExplicit() {
        ChatTurnPipeline pipeline = newPipeline(
                mockNamed(EmitRouteStage.class, "emit_route"),
                mockNamed(LoadDialogueContextStage.class, "load_dialogue"),
                mockNamed(OrchestrateStage.class, "orchestrate"),
                mockNamed(StreamFinalAnswerStage.class, "stream_final"),
                mockNamed(QualityLoopStage.class, "quality_loop"));
        assertEquals(
                List.of("emit_route", "load_dialogue", "orchestrate", "stream_final", "quality_loop"),
                pipeline.stageNames());
    }

    @Test
    void insertingNoopAfterRouteDoesNotAddEvents() {
        ChatTurnContext ctx = ChatTurnContext.open("hi", "hi", "hi", "chat-1", null, false, null);
        AtomicBoolean noopRan = new AtomicBoolean(false);
        ChatTurnStage noop = new ChatTurnStage() {
            @Override
            public String name() {
                return "noop";
            }

            @Override
            public Mono<Void> execute(ChatTurnContext c) {
                noopRan.set(true);
                return Mono.empty();
            }
        };
        List<ChatTurnStage> stages = new ArrayList<>();
        stages.add(new EmitRouteStage());
        stages.add(noop);
        Flux.fromIterable(stages).concatMap(s -> s.execute(ctx)).blockLast();

        assertTrue(noopRan.get());
        assertEquals(1, ctx.getAccumulated().size());
        assertEquals("route", ctx.getAccumulated().get(0).type());
        assertEquals(ChatTurnContext.AGENT_MODE_ORCHESTRATE, ctx.getAccumulated().get(0).name());
    }

    private static ChatTurnPipeline newPipeline(
            EmitRouteStage emit,
            LoadDialogueContextStage load,
            OrchestrateStage orch,
            StreamFinalAnswerStage stream,
            QualityLoopStage quality) {
        return new ChatTurnPipeline(
                emit, load, orch, stream, quality,
                mock(ChatTurnFinalizer.class),
                new ChatStreamEventWriter());
    }

    private static <T extends ChatTurnStage> T mockNamed(Class<T> type, String name) {
        T stage = mock(type);
        when(stage.name()).thenReturn(name);
        return stage;
    }
}
