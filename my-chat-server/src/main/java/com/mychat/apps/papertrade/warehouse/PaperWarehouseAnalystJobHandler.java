package com.mychat.apps.papertrade.warehouse;

import com.mychat.entity.po.AsyncJob;
import com.mychat.job.JobHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 票仓分析员后台任务。 */
@Slf4j
@Component
public class PaperWarehouseAnalystJobHandler implements JobHandler {

    private final PaperWarehouseAnalystService analystService;

    public PaperWarehouseAnalystJobHandler(@Lazy PaperWarehouseAnalystService analystService) {
        this.analystService = analystService;
    }

    @Override
    public String type() {
        return PaperWarehouseAnalystService.JOB_TYPE;
    }

    @Override
    public void execute(AsyncJob job) throws Exception {
        String runId = job.getRefId();
        if (!StringUtils.hasText(runId)) {
            throw new IllegalArgumentException("paper_trade_warehouse_analyst 缺少 refId");
        }
        log.info("开始票仓分析 runId={} jobId={}", runId, job.getId());
        analystService.run(runId);
    }
}
