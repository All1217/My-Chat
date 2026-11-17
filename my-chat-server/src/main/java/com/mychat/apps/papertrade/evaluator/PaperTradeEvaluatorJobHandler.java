package com.mychat.apps.papertrade.evaluator;

import com.mychat.entity.po.AsyncJob;
import com.mychat.job.JobHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 交易评估员后台任务。 */
@Slf4j
@Component
public class PaperTradeEvaluatorJobHandler implements JobHandler {

    private final PaperTradeEvaluatorService evaluatorService;

    public PaperTradeEvaluatorJobHandler(@Lazy PaperTradeEvaluatorService evaluatorService) {
        this.evaluatorService = evaluatorService;
    }

    @Override
    public String type() {
        return PaperTradeEvaluatorService.JOB_TYPE;
    }

    @Override
    public void execute(AsyncJob job) throws Exception {
        String runId = job.getRefId();
        if (!StringUtils.hasText(runId)) {
            throw new IllegalArgumentException("paper_trade_evaluator 缺少 refId");
        }
        log.info("开始交易评估 runId={} jobId={}", runId, job.getId());
        evaluatorService.run(runId);
    }
}
