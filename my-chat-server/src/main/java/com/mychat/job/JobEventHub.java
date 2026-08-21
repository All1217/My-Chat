package com.mychat.job;

import com.mychat.vo.AsyncJobVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 进程内 SSE 扇出：任务状态变化推给所有已连接的前端。
 * <p>
 * 心跳注释防止 Vite / 反向代理因空闲断开；新连接不重放历史（前端先拉 /active）。
 */
@Slf4j
@Component
public class JobEventHub {

    /** 24h，配合心跳；前端 EventSource 仍会在断线后自动重连 */
    private static final long EMITTER_TIMEOUT_MS = 24 * 60 * 60 * 1000L;

    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        try {
            // 立即写一行注释，确认流已打开
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (IOException e) {
            emitters.remove(emitter);
            emitter.completeWithError(e);
        }
        log.debug("SSE 订阅数={}", emitters.size());
        return emitter;
    }

    public void emit(AsyncJobVO vo) {
        if (vo == null) {
            return;
        }
        List<SseEmitter> dead = new ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("job")
                        .data(vo, MediaType.APPLICATION_JSON));
            } catch (Exception e) {
                dead.add(emitter);
            }
        }
        if (!dead.isEmpty()) {
            emitters.removeAll(dead);
        }
    }

    /** 每 15s 注释心跳，避免代理掐断空闲 SSE */
    @Scheduled(fixedRate = 15_000)
    public void heartbeat() {
        if (emitters.isEmpty()) {
            return;
        }
        List<SseEmitter> dead = new ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().comment("ping"));
            } catch (Exception e) {
                dead.add(emitter);
            }
        }
        if (!dead.isEmpty()) {
            emitters.removeAll(dead);
        }
    }
}
