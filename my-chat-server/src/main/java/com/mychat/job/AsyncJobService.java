package com.mychat.job;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mychat.entity.po.AsyncJob;
import com.mychat.vo.AsyncJobVO;

import java.time.Duration;
import java.util.List;

public interface AsyncJobService extends IService<AsyncJob> {

    /**
     * 落库 PENDING 后立刻异步执行，HTTP 不等待 Handler。
     * 成功时默认弹窗+音效。
     */
    AsyncJobVO submit(String jobType, String title, String refId, String payload);

    /**
     * @param notifyOnSuccess {@code false} 时成功不弹窗不播声；失败始终提醒
     */
    AsyncJobVO submit(String jobType, String title, String refId, String payload, boolean notifyOnSuccess);

    List<AsyncJobVO> listActive();

    /**
     * 启动时把长时间卡在 PENDING/RUNNING 的任务标 FAILED。
     *
     * @return 更新条数
     */
    int failStaleJobs(Duration olderThan);
}
