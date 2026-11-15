-- 模拟炒股：搜集员 / 交易员定时配置（单行，账户设置可改）。不改 V1–V5。

SET search_path TO public;

CREATE TABLE paper_schedule_settings
(
    id               VARCHAR(64) PRIMARY KEY,
    research_enabled BOOLEAN      NOT NULL DEFAULT FALSE,
    research_cron    VARCHAR(100) NOT NULL,
    trader_enabled   BOOLEAN      NOT NULL DEFAULT FALSE,
    trader_cron      VARCHAR(100) NOT NULL,
    zone_id          VARCHAR(64)  NOT NULL DEFAULT 'Asia/Shanghai',
    updated_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE paper_schedule_settings IS '模拟炒股定时任务配置（单行 default）';
COMMENT ON COLUMN paper_schedule_settings.id IS '固定 default';
COMMENT ON COLUMN paper_schedule_settings.research_enabled IS '是否启用搜集员定时';
COMMENT ON COLUMN paper_schedule_settings.research_cron IS '搜集员 Spring cron（6 域）';
COMMENT ON COLUMN paper_schedule_settings.trader_enabled IS '是否启用交易员盘后定时';
COMMENT ON COLUMN paper_schedule_settings.trader_cron IS '交易员 Spring cron（6 域）';
COMMENT ON COLUMN paper_schedule_settings.zone_id IS '解释 cron 的时区，如 Asia/Shanghai';
COMMENT ON COLUMN paper_schedule_settings.updated_at IS '更新时间';
