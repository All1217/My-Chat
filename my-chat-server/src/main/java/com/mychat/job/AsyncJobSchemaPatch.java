package com.mychat.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 已有库不会因 schema.sql 的 CREATE IF NOT EXISTS 自动加列；
 * 启动时补 notify_on_success，避免 StaleJobRecovery 查询失败。
 */
@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class AsyncJobSchemaPatch implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.execute("""
                ALTER TABLE async_job
                ADD COLUMN IF NOT EXISTS notify_on_success BOOLEAN NOT NULL DEFAULT TRUE
                """);
        log.debug("async_job.notify_on_success 列已就绪");
    }
}
