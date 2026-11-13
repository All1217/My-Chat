package com.mychat.apps.papertrade.research;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 工作日盘后搜集一轮。与操盘定时分开，默认关闭。 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.paper-trade", name = "research-schedule-enabled", havingValue = "true")
public class PaperResearchSchedule {

    private final PaperResearchService researchService;

    @Scheduled(cron = "${app.paper-trade.research-schedule-cron:0 10 15 * * MON-FRI}",
            zone = "${app.paper-trade.schedule-zone:Asia/Shanghai}")
    public void gather() {
        try {
            log.info("盘后定时触发 paper_trade_research");
            researchService.submitRun();
        } catch (Exception e) {
            log.warn("搜集轮提交失败: {}", e.getMessage());
        }
    }
}
