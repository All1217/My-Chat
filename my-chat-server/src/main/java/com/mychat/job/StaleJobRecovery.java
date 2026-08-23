package com.mychat.job;

import com.mychat.job.AsyncJobService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 进程崩溃后 PENDING/RUNNING 可能永远卡住；启动时把过期行标 FAILED。
 */
@Slf4j
@Component
public class StaleJobRecovery implements ApplicationRunner {

    private final AsyncJobService asyncJobService;
    private final long staleMinutes;

    public StaleJobRecovery(
            AsyncJobService asyncJobService,
            @Value("${app.jobs.stale-minutes:10}") long staleMinutes) {
        this.asyncJobService = asyncJobService;
        this.staleMinutes = staleMinutes;
    }

    @Override
    public void run(ApplicationArguments args) {
        int n = asyncJobService.failStaleJobs(Duration.ofMinutes(staleMinutes));
        log.info("过期任务回收完成 staleMinutes={} staleCount={}", staleMinutes, n);
    }
}
