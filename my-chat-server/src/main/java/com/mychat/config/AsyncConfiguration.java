package com.mychat.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 后台任务线程池与调度（SSE 心跳、过期任务回收）。
 * <p>
 * 与 Tomcat 虚拟线程分离：ingest / demo 等 CPU+IO 混合任务用有界池，避免打满外部 API。
 */
@Slf4j
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfiguration {

    public static final String JOB_EXECUTOR = "jobExecutor";

    @Bean(name = JOB_EXECUTOR)
    public Executor jobExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("job-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        log.info("jobExecutor 已启动 core=2 max=4 queue=100");
        return executor;
    }
}
