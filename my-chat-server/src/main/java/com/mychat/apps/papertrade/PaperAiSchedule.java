package com.mychat.apps.papertrade;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 可选盘后定时触发 AI 一轮（默认关；不引入 Quartz）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.paper-trade", name = "schedule-enabled", havingValue = "true")
public class PaperAiSchedule {

    private final PaperAiTraderService traderService;

    @Scheduled(cron = "${app.paper-trade.schedule-cron:0 35 15 * * MON-FRI}")
    public void afterCloseRound() {
        try {
            log.info("盘后定时触发 paper_trade_ai_round");
            traderService.submitRun();
        } catch (Exception e) {
            log.warn("盘后 AI 轮提交失败: {}", e.getMessage());
        }
    }
}
