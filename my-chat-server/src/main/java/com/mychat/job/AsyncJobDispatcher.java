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
 * 异步任务执行器。
 * <p>
 * {@code submit} 只把任务写入数据库并马上返回；真正干活的是本类：
 * 在独立线程里把任务标成进行中、按类型找到对应 {@link JobHandler} 执行，
 * 成功/失败后再改库，并通过 {@link JobEventHub} 推给前端。
 * <p>
 * 必须做成单独的 Spring Bean：{@code @Async} 只有「别人调用这个 Bean」才生效。
 * 若写在 Service 里再用 {@code this.dispatch()}，会变成同步执行，请求会卡住。
 */
@Slf4j
@Component
public class AsyncJobDispatcher {

    /** 失败原因写入数据库时的最长字数，避免异常堆栈撑爆字段 */
    private static final int ERROR_MAX_CHARS = 1000;

    private final AsyncJobMapper asyncJobMapper;
    private final JobEventHub jobEventHub;
    /** job_type → 具体业务实现，启动时从所有 JobHandler Bean 收集 */
    private final Map<String, JobHandler> handlers;

    /**
     * 注入 Mapper、SSE 通道，并把所有 Handler 按 type 编成字典，方便 dispatch 时查找。
     */
    public AsyncJobDispatcher(
            AsyncJobMapper asyncJobMapper,
            JobEventHub jobEventHub,
            List<JobHandler> handlerList) {
        this.asyncJobMapper = asyncJobMapper;
        this.jobEventHub = jobEventHub;
        this.handlers = handlerList.stream()
                .collect(Collectors.toMap(JobHandler::type, Function.identity(), (a, b) -> a));
    }

    /**
     * 在任务线程池里执行一条任务（HTTP 线程不会等这里跑完）。
     * <p>
     * 流程：抢成 RUNNING → 按 job_type 找 Handler → 跑完标成功，出错标失败，每步都推 SSE。
     */
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

    /**
     * 只有仍是 PENDING 时才改成 RUNNING。
     *
     * @return {@code false} 表示别人已经在跑或已结束，本次不要再执行
     */
    private boolean markRunning(String jobId) {
        LambdaUpdateWrapper<AsyncJob> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(AsyncJob::getId, jobId)
                .eq(AsyncJob::getStatus, AsyncJob.STATUS_PENDING)
                .set(AsyncJob::getStatus, AsyncJob.STATUS_RUNNING)
                .set(AsyncJob::getUpdatedAt, LocalDateTime.now());
        return asyncJobMapper.update(null, wrapper) > 0;
    }

    /**
     * 把任务标成最终结果（成功或失败），记下结束时间，并通知前端弹窗。
     */
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

    /** 截断失败原因，空则给一句默认文案。 */
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
