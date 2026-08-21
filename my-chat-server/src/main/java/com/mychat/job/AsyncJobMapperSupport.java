package com.mychat.job;

import com.mychat.entity.po.AsyncJob;
import com.mychat.vo.AsyncJobVO;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Entity → 前端/SSE 载荷 */
public final class AsyncJobMapperSupport {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private AsyncJobMapperSupport() {
    }

    public static AsyncJobVO toVo(AsyncJob job) {
        if (job == null) {
            return null;
        }
        AsyncJobVO vo = new AsyncJobVO();
        vo.setId(job.getId());
        vo.setJobType(job.getJobType());
        vo.setStatus(job.getStatus());
        vo.setTitle(job.getTitle());
        vo.setRefId(job.getRefId());
        vo.setErrorMessage(job.getErrorMessage());
        vo.setCreatedAt(format(job.getCreatedAt()));
        vo.setUpdatedAt(format(job.getUpdatedAt()));
        vo.setFinishedAt(format(job.getFinishedAt()));
        return vo;
    }

    private static String format(LocalDateTime t) {
        return t == null ? null : FMT.format(t);
    }
}
