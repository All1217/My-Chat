package com.mychat.apps.papertrade.research;

import com.mychat.entity.po.AsyncJob;
import com.mychat.job.JobHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 模拟炒股信息搜集后台任务。 */
@Slf4j
@Component
public class PaperResearchJobHandler implements JobHandler {

    private final PaperResearchService researchService;

    public PaperResearchJobHandler(@Lazy PaperResearchService researchService) {
        this.researchService = researchService;
    }

    @Override
    public String type() {
        return PaperResearchService.JOB_TYPE;
    }

    @Override
    public void execute(AsyncJob job) throws Exception {
        String runId = job.getRefId();
        if (!StringUtils.hasText(runId)) {
            throw new IllegalArgumentException("paper_trade_research 缺少 refId");
        }
        log.info("开始搜集 runId={} jobId={}", runId, job.getId());
        researchService.run(runId);
    }
}
