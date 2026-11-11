package com.mychat.apps.papertrade;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** 启动时确保默认纸面策略已 seed。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaperStrategySeedRunner implements ApplicationRunner {

    private final PaperStrategyService strategyService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            strategyService.requireDefault();
            log.info("paper_strategy 默认策略已就绪");
        } catch (Exception e) {
            log.warn("paper_strategy seed 跳过: {}", e.getMessage());
        }
    }
}
