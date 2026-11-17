-- 票仓清理/分析员定时 + AI 工具写库审计。不改 V1–V11。

SET search_path TO public;

ALTER TABLE paper_schedule_settings
    ADD COLUMN IF NOT EXISTS cleanup_enabled BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE paper_schedule_settings
    ADD COLUMN IF NOT EXISTS cleanup_cron VARCHAR(100) NOT NULL DEFAULT '0 0 23 * * *';
ALTER TABLE paper_schedule_settings
    ADD COLUMN IF NOT EXISTS analyst_enabled BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE paper_schedule_settings
    ADD COLUMN IF NOT EXISTS analyst_cron VARCHAR(100) NOT NULL DEFAULT '0 0 18 * * *';
ALTER TABLE paper_schedule_settings
    ADD COLUMN IF NOT EXISTS warehouse_tier2_retain_days INTEGER NOT NULL DEFAULT 2;

COMMENT ON COLUMN paper_schedule_settings.cleanup_enabled IS '是否启用票仓清理定时';
COMMENT ON COLUMN paper_schedule_settings.cleanup_cron IS '票仓清理 Spring cron（6 域），默认每天 23:00';
COMMENT ON COLUMN paper_schedule_settings.analyst_enabled IS '是否启用票仓分析员定时';
COMMENT ON COLUMN paper_schedule_settings.analyst_cron IS '票仓分析员 Spring cron（6 域），默认每天 18:00';
COMMENT ON COLUMN paper_schedule_settings.warehouse_tier2_retain_days IS
    '价值等级=2 滞留天数阈值（自然日：当前日-创建日），默认 2';

CREATE TABLE IF NOT EXISTS paper_warehouse_cleanup_run
(
    id              VARCHAR(64) PRIMARY KEY,
    job_id          VARCHAR(64),
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    stats_json      TEXT,
    framework_trace TEXT,
    error_message   TEXT,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    finished_at     TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_paper_warehouse_cleanup_run_created
    ON paper_warehouse_cleanup_run (created_at DESC);
COMMENT ON TABLE paper_warehouse_cleanup_run IS '票仓清理一轮（无 AI）';

CREATE TABLE IF NOT EXISTS paper_warehouse_analyst_run
(
    id              VARCHAR(64) PRIMARY KEY,
    job_id          VARCHAR(64),
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    screened_count  INTEGER     NOT NULL DEFAULT 0,
    adjusted_count  INTEGER     NOT NULL DEFAULT 0,
    stats_json      TEXT,
    framework_trace TEXT,
    error_message   TEXT,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    finished_at     TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_paper_warehouse_analyst_run_created
    ON paper_warehouse_analyst_run (created_at DESC);
COMMENT ON TABLE paper_warehouse_analyst_run IS '票仓分析员一轮（Agent 深搜调等级）';

CREATE TABLE IF NOT EXISTS paper_ai_tool_audit
(
    id            VARCHAR(64) PRIMARY KEY,
    scenario_id   VARCHAR(64),
    agent_run_id  VARCHAR(64),
    tool_name     VARCHAR(128) NOT NULL,
    table_name    VARCHAR(128) NOT NULL,
    op            VARCHAR(32)  NOT NULL,
    symbol        VARCHAR(32),
    args_json     TEXT,
    result_json   TEXT,
    success       BOOLEAN      NOT NULL DEFAULT TRUE,
    error_message TEXT,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_paper_ai_tool_audit_created
    ON paper_ai_tool_audit (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_paper_ai_tool_audit_table_created
    ON paper_ai_tool_audit (table_name, created_at DESC);
COMMENT ON TABLE paper_ai_tool_audit IS 'Agent 工具链写库审计（仅 @Tool 写操作）';
