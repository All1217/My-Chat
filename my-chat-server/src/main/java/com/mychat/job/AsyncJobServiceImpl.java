package com.mychat.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mychat.entity.po.AsyncJob;
import com.mychat.mapper.AsyncJobMapper;
import com.mychat.vo.AsyncJobVO;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 异步任务的「前台窗口」。
 * <p>
 * 业务只跟本类打交道：提交任务、查还在跑的任务、启动时清理僵尸任务。
 * 真正干活交给 {@link AsyncJobDispatcher}，推给浏览器交给 {@link com.mychat.job.JobEventHub}。
 * <p>
 * {@code submit} 写入数据库后立刻返回，不会等 Handler 跑完，所以 HTTP 不会被向量化这类慢活堵住。
 */
@Slf4j
@Service
@AllArgsConstructor
public class AsyncJobServiceImpl extends ServiceImpl<AsyncJobMapper, AsyncJob> implements AsyncJobService {

    private final AsyncJobMapper asyncJobMapper;
    private final AsyncJobDispatcher asyncJobDispatcher;

    /**
     * 登记一条新任务并交给后台去跑。
     * <p>
     * 先校验、再 insert（状态 PENDING），然后 {@code dispatch}（异步，不等待），
     * 最后把刚写入的记录返回给调用方（此时多半还是 PENDING）。
     *
     * @param jobType 业务类型，要和某个 {@code JobHandler.type()} 对得上
     * @param title   用户看见的通知标题
     * @param refId   业务主键，例如文档 ID，没有就传 null
     * @param payload Handler 要用的 JSON 入参，没有就传 null
     */
    @Override
    public AsyncJobVO submit(String jobType, String title, String refId, String payload) {
        if (!StringUtils.hasText(jobType)) {
            throw new IllegalArgumentException("jobType 不能为空");
        }
        if (!StringUtils.hasText(title)) {
            throw new IllegalArgumentException("title 不能为空");
        }
        AsyncJob job = new AsyncJob();
        job.setId(UUID.randomUUID().toString());
        job.setJobType(jobType.trim());
        job.setStatus(AsyncJob.STATUS_PENDING);
        job.setTitle(title.trim());
        job.setRefId(StringUtils.hasText(refId) ? refId.trim() : null);
        job.setPayload(payload);
        asyncJobMapper.insert(job);
        log.info("提交任务 id={} type={} title={}", job.getId(), job.getJobType(), job.getTitle());
        asyncJobDispatcher.dispatch(job.getId());
        return AsyncJobMapperSupport.toVo(asyncJobMapper.selectById(job.getId()));
    }

    /**
     * 列出还没结束的任务（PENDING / RUNNING）。
     * 前端刷新页面后先调这个补洞，因为 SSE 只推「连上之后」的新变化。
     */
    @Override
    public List<AsyncJobVO> listActive() {
        LambdaQueryWrapper<AsyncJob> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(AsyncJob::getStatus, AsyncJob.STATUS_PENDING, AsyncJob.STATUS_RUNNING)
                .orderByDesc(AsyncJob::getCreatedAt);
        return asyncJobMapper.selectList(wrapper).stream()
                .map(AsyncJobMapperSupport::toVo)
                .toList();
    }

    /**
     * 把太久没动静的未完成任务标成失败。
     * 进程中途挂了时，库里会留下永远 PENDING/RUNNING 的行；启动时扫一遍，避免僵尸任务占着。
     *
     * @param olderThan 超过此时长未更新就视为卡住
     * @return 这次改掉了几条
     */
    @Override
    public int failStaleJobs(Duration olderThan) {
        LocalDateTime cutoff = LocalDateTime.now().minus(olderThan);
        LambdaUpdateWrapper<AsyncJob> wrapper = new LambdaUpdateWrapper<>();
        wrapper.in(AsyncJob::getStatus, AsyncJob.STATUS_PENDING, AsyncJob.STATUS_RUNNING)
                .lt(AsyncJob::getUpdatedAt, cutoff)
                .set(AsyncJob::getStatus, AsyncJob.STATUS_FAILED)
                .set(AsyncJob::getErrorMessage, "任务超时未完成（进程重启或卡住）")
                .set(AsyncJob::getFinishedAt, LocalDateTime.now())
                .set(AsyncJob::getUpdatedAt, LocalDateTime.now());
        int n = asyncJobMapper.update(null, wrapper);
        if (n > 0) {
            log.warn("已将 {} 条过期任务标为 FAILED cutoff={}", n, cutoff);
        }
        return n;
    }
}
