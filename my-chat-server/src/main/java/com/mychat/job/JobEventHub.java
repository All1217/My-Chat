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
 * 任务通知的「广播站」。
 * <p>
 * 前端打开页面后会连上 {@code GET /ai/jobs/events}，本类把这条长连接记下来。
 * {@link AsyncJobDispatcher} 改完任务状态后调用 {@link #emit}，这里向所有在线标签页各推一条，
 * 用户即使已经离开上传页也能收到完成弹窗。
 * <p>
 * 只推「从现在开始」的变化，不重放历史；刷新后前端先请求 {@code /ai/jobs/active} 补未完成任务。
 * 每隔一段时间发心跳，防止 Vite / Nginx 把长时间没数据的连接掐掉。
 */
@Slf4j
@Component
public class JobEventHub {

    /** 单条连接最长挂 24 小时；断了之后浏览器 EventSource 会自己重连 */
    private static final long EMITTER_TIMEOUT_MS = 24 * 60 * 60 * 1000L;

    /** 当前所有打开着的前端连接。CopyOnWrite：推送时别人同时订阅/断开也不怕 */
    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    /**
     * 新标签页连上来：登记这条连接，关掉/出错时自动从名单里拿掉。
     *
     * @return 交给 Controller 的 SSE 句柄，之后就靠它往浏览器写数据
     */
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

    /**
     * 把一条任务状态发给所有在线前端。
     * 某条连接已经断了就记下来，循环结束后统一移除，避免名单越积越脏。
     */
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

    /**
     * 每 15 秒发一行不会显示的 ping。
     * 连接太久没字节，中间的代理会以为死了；心跳只为保活，前端不用处理。
     */
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
