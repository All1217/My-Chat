package com.mychat.apps.papertrade.schedule;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** 空表时写入默认定时配置（早于 PaperScheduleManager）。 */
@Slf4j
@Component
@Order(35)
@RequiredArgsConstructor
public class PaperScheduleSeedRunner implements ApplicationRunner {

    private final PaperScheduleService scheduleService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            scheduleService.seedIfAbsent();
            log.info("paper_schedule_settings 已就绪");
        } catch (Exception e) {
            log.warn("paper_schedule_settings seed 跳过: {}", e.getMessage());
        }
    }
}
