package com.mychat.utils;

import com.mychat.common.ChatStreamEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 主聊天 / Demo NDJSON 流共用的 sink 辅助。
 * <p>
 * 无业务依赖：只负责「事件入 accumulated + tryEmit」与「sink 行流 merge drive」。
 */
public final class NdjsonStreamSupport {

    private NdjsonStreamSupport() {
    }

    /**
     * 将事件写入时间线快照并推入 sink（供前端 NDJSON 与回合落库共用同一序）。
     */
    public static void emitTracked(
            Sinks.Many<ChatStreamEvent> sink,
            List<ChatStreamEvent> accumulated,
            ChatStreamEvent event) {
        accumulated.add(event);
        sink.tryEmitNext(event);
    }

    public static void emitError(
            Sinks.Many<ChatStreamEvent> sink,
            String turnId,
            AtomicInteger seq,
            Throwable e,
            List<ChatStreamEvent> accumulated) {
        String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        emitTracked(sink, accumulated, ChatStreamEvent.error(turnId, seq, msg));
    }

    /**
     * sink 事件转 NDJSON 行，与后台 {@code drive} 并行；drive 结束后不再追加行。
     */
    public static Flux<String> mergeNdjson(
            Sinks.Many<ChatStreamEvent> sink,
            Mono<Void> drive,
            ChatStreamEventWriter eventWriter) {
        return Flux.merge(
                sink.asFlux().map(eventWriter::toLine),
                drive.thenMany(Flux.empty())
        );
    }
}
