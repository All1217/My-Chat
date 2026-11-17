-- 交易评估员定时 + 收件人。不改 V1–V13。

SET search_path TO public;

ALTER TABLE paper_schedule_settings
    ADD COLUMN IF NOT EXISTS evaluator_enabled BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE paper_schedule_settings
    ADD COLUMN IF NOT EXISTS evaluator_cron VARCHAR(100) NOT NULL DEFAULT '0 0 10 * * SAT';
ALTER TABLE paper_schedule_settings
    ADD COLUMN IF NOT EXISTS evaluator_email VARCHAR(256) NOT NULL DEFAULT '3116459510@qq.com';

COMMENT ON COLUMN paper_schedule_settings.evaluator_enabled IS '是否启用交易评估员定时';
COMMENT ON COLUMN paper_schedule_settings.evaluator_cron IS '交易评估员 Spring cron（6 域），默认每周六 10:00';
COMMENT ON COLUMN paper_schedule_settings.evaluator_email IS '评估结果收件人，默认 3116459510@qq.com';

CREATE TABLE IF NOT EXISTS paper_trade_evaluator_run
(
    id                VARCHAR(64) PRIMARY KEY,
    job_id            VARCHAR(64),
    status            VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    trigger_type      VARCHAR(32)  NOT NULL DEFAULT 'WEEKLY',
    stage_summary_id  VARCHAR(64),
    email_status      VARCHAR(32),
    stats_json        TEXT,
    framework_trace   TEXT,
    error_message     TEXT,
    created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    finished_at       TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_paper_trade_evaluator_run_created
    ON paper_trade_evaluator_run (created_at DESC);
COMMENT ON TABLE paper_trade_evaluator_run IS '交易评估员一轮（周更或枯竭唤醒）';
