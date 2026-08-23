package com.mychat.controller;

import com.mychat.common.result.Result;
import com.mychat.job.JobEventHub;
import com.mychat.job.AsyncJobService;
import com.mychat.vo.AsyncJobVO;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 异步任务查询与 SSE 订阅。完成弹窗由前端全局 notifyStore 消费，不绑具体业务页。
 */
@Slf4j
@RestController
@RequestMapping("/ai/jobs")
@RequiredArgsConstructor
public class JobController {

    private final AsyncJobService asyncJobService;
    private final JobEventHub jobEventHub;

    /**
     * 未完成任务（刷新后补洞）。SSE 只推增量，不重放历史。
     */
    @GetMapping("/active")
    public Result<List<AsyncJobVO>> active() {
        return Result.ok(asyncJobService.listActive());
    }

    /**
     * 任务状态 SSE。事件名 {@code job}，data 为 {@link AsyncJobVO} JSON。
     * <p>
     * 注意：本接口不是 {@link Result} 信封，前端必须用 EventSource，不要走 axios 解包。
     */
    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(HttpServletResponse response) {
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
        return jobEventHub.subscribe();
    }
}
