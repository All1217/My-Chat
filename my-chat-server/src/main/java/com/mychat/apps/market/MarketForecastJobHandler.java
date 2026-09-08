package com.mychat.apps.market;

import com.mychat.entity.po.AsyncJob;
import com.mychat.job.JobHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 股市走势预测后台任务：按 refId 读 market_forecast，调用 Agent 写回预测点。
 */
@Slf4j
@Component
public class MarketForecastJobHandler implements JobHandler {

    private final MarketForecastService marketForecastService;

    public MarketForecastJobHandler(@Lazy MarketForecastService marketForecastService) {
        this.marketForecastService = marketForecastService;
    }

    @Override
    public String type() {
        return MarketForecastService.JOB_TYPE;
    }

    @Override
    public void execute(AsyncJob job) throws Exception {
        String forecastId = job.getRefId();
        if (!StringUtils.hasText(forecastId)) {
            throw new IllegalArgumentException("market_forecast 缺少 refId");
        }
        log.info("开始预测走势 forecastId={} jobId={}", forecastId, job.getId());
        marketForecastService.runForecast(forecastId);
    }
}
