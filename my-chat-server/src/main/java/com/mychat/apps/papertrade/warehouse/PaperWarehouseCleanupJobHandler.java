package com.mychat.apps.papertrade.warehouse;

import com.mychat.entity.po.AsyncJob;
import com.mychat.job.JobHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 票仓清理后台任务。 */
@Slf4j
@Component
public class PaperWarehouseCleanupJobHandler implements JobHandler {

    private final PaperWarehouseCleanupService cleanupService;

    public PaperWarehouseCleanupJobHandler(@Lazy PaperWarehouseCleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    @Override
    public String type() {
        return PaperWarehouseCleanupService.JOB_TYPE;
    }

    @Override
    public void execute(AsyncJob job) throws Exception {
        String runId = job.getRefId();
        if (!StringUtils.hasText(runId)) {
            throw new IllegalArgumentException("paper_trade_warehouse_cleanup 缺少 refId");
        }
        log.info("开始票仓清理 runId={} jobId={}", runId, job.getId());
        cleanupService.run(runId);
    }
}
