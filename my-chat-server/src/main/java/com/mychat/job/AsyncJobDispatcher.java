package com.mychat.job;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.mychat.config.AsyncConfiguration;
import com.mychat.entity.po.AsyncJob;
import com.mychat.mapper.AsyncJobMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 在独立 Bean 上 {@code @Async}，避免 Service 自调用导致异步失效。
 */
@Slf4j
@Component
public class AsyncJobDispatcher {

    private static final int ERROR_MAX_CHARS = 1000;

    private final AsyncJobMapper asyncJobMapper;
    private final JobEventHub jobEventHub;
    private final Map<String, JobHandler> handlers;

    public AsyncJobDispatcher(
            AsyncJobMapper asyncJobMapper,
            JobEventHub jobEventHub,
            List<JobHandler> handlerList) {
        this.asyncJobMapper = asyncJobMapper;
        this.jobEventHub = jobEventHub;
        this.handlers = handlerList.stream()
                .collect(Collectors.toMap(JobHandler::type, Function.identity(), (a, b) -> a));
    }

    @Async(AsyncConfiguration.JOB_EXECUTOR)
    public void dispatch(String jobId) {
        AsyncJob job = asyncJobMapper.selectById(jobId);
        if (job == null) {
            log.warn("任务不存在 jobId={}", jobId);
            return;
        }
        // 1. PENDING → RUNNING；并发重复调度时 update=0 则跳过
        if (!markRunning(jobId)) {
            log.info("跳过非 PENDING 任务 jobId={} status={}", jobId, job.getStatus());
            return;
        }
        job = asyncJobMapper.selectById(jobId);
        jobEventHub.emit(AsyncJobMapperSupport.toVo(job));

        // 2. 按 type 找 Handler
        JobHandler handler = handlers.get(job.getJobType());
        if (handler == null) {
            markTerminal(jobId, AsyncJob.STATUS_FAILED, "未知 job_type: " + job.getJobType());
            return;
        }

        // 3. 执行；异常写入 FAILED
        try {
            handler.execute(job);
            markTerminal(jobId, AsyncJob.STATUS_SUCCEEDED, null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            markTerminal(jobId, AsyncJob.STATUS_FAILED, "任务被中断");
        } catch (Exception e) {
            log.warn("任务失败 jobId={} type={}: {}", jobId, job.getJobType(), e.getMessage());
            markTerminal(jobId, AsyncJob.STATUS_FAILED, truncate(e.getMessage()));
        }
    }

    private boolean markRunning(String jobId) {
        LambdaUpdateWrapper<AsyncJob> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(AsyncJob::getId, jobId)
                .eq(AsyncJob::getStatus, AsyncJob.STATUS_PENDING)
                .set(AsyncJob::getStatus, AsyncJob.STATUS_RUNNING)
                .set(AsyncJob::getUpdatedAt, LocalDateTime.now());
        return asyncJobMapper.update(null, wrapper) > 0;
    }

    private void markTerminal(String jobId, String status, String errorMessage) {
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<AsyncJob> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(AsyncJob::getId, jobId)
                .set(AsyncJob::getStatus, status)
                .set(AsyncJob::getErrorMessage, errorMessage)
                .set(AsyncJob::getFinishedAt, now)
                .set(AsyncJob::getUpdatedAt, now);
        asyncJobMapper.update(null, wrapper);
        AsyncJob latest = asyncJobMapper.selectById(jobId);
        jobEventHub.emit(AsyncJobMapperSupport.toVo(latest));
        log.info("任务终态 jobId={} status={}", jobId, status);
    }

    private static String truncate(String message) {
        if (message == null) {
            return "执行失败";
        }
        String t = message.trim();
        if (t.length() <= ERROR_MAX_CHARS) {
            return t;
        }
        return t.substring(0, ERROR_MAX_CHARS) + "…";
    }
}
