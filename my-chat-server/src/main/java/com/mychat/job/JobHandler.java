package com.mychat.job;

import com.mychat.entity.po.AsyncJob;

/**
 * 后台任务执行器。每种 {@code job_type} 一个实现，由 {@link AsyncJobDispatcher} 分发。
 */
public interface JobHandler {

    /** 与 {@code async_job.job_type} 一致 */
    String type();

    /**
     * 执行任务。成功则正常返回；失败请抛异常，由调度器写入 FAILED。
     */
    void execute(AsyncJob job) throws Exception;
}
