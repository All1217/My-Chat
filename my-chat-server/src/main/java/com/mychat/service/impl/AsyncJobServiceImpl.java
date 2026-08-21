package com.mychat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mychat.entity.po.AsyncJob;
import com.mychat.job.AsyncJobDispatcher;
import com.mychat.job.AsyncJobMapperSupport;
import com.mychat.mapper.AsyncJobMapper;
import com.mychat.service.AsyncJobService;
import com.mychat.vo.AsyncJobVO;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@AllArgsConstructor
public class AsyncJobServiceImpl extends ServiceImpl<AsyncJobMapper, AsyncJob> implements AsyncJobService {

    private final AsyncJobMapper asyncJobMapper;
    private final AsyncJobDispatcher asyncJobDispatcher;

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

    @Override
    public List<AsyncJobVO> listActive() {
        LambdaQueryWrapper<AsyncJob> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(AsyncJob::getStatus, AsyncJob.STATUS_PENDING, AsyncJob.STATUS_RUNNING)
                .orderByDesc(AsyncJob::getCreatedAt);
        return asyncJobMapper.selectList(wrapper).stream()
                .map(AsyncJobMapperSupport::toVo)
                .toList();
    }

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
