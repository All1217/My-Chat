package com.mychat.apps.market;

import com.mychat.entity.po.AsyncJob;
import com.mychat.job.JobHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 美股大回撤预警后台任务：按 refId（ISO 周）联网检索并写回文件。 */
@Slf4j
@Component
public class MarketCrashRiskJobHandler implements JobHandler {

    private final MarketCrashRiskService marketCrashRiskService;

    public MarketCrashRiskJobHandler(@Lazy MarketCrashRiskService marketCrashRiskService) {
        this.marketCrashRiskService = marketCrashRiskService;
    }

    @Override
    public String type() {
        return MarketCrashRiskService.JOB_TYPE;
    }

    @Override
    public void execute(AsyncJob job) {
        String weekKey = job.getRefId();
        if (!StringUtils.hasText(weekKey)) {
            throw new IllegalArgumentException("market_crash_risk 缺少 refId");
        }
        log.info("开始美股回撤预警 weekKey={} jobId={}", weekKey, job.getId());
        marketCrashRiskService.runRefresh(weekKey);
    }
}
