package com.mychat.apps.papertrade.trader;

import com.mychat.entity.po.AsyncJob;
import com.mychat.job.JobHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 模拟炒股 AI 一轮后台任务。 */
@Slf4j
@Component
public class PaperTradeAiRoundJobHandler implements JobHandler {

    private final PaperAiTraderService traderService;

    public PaperTradeAiRoundJobHandler(@Lazy PaperAiTraderService traderService) {
        this.traderService = traderService;
    }

    @Override
    public String type() {
        return PaperAiTraderService.JOB_TYPE;
    }

    @Override
    public void execute(AsyncJob job) throws Exception {
        String runId = job.getRefId();
        if (!StringUtils.hasText(runId)) {
            throw new IllegalArgumentException("paper_trade_ai_round 缺少 refId");
        }
        log.info("开始纸面 AI 轮 runId={} jobId={}", runId, job.getId());
        traderService.runRound(runId);
    }
}
