-- My-Chat 股市预测结构
-- Flyway V2：依赖 V1 中的通用异步任务表。

SET search_path TO public;

CREATE TABLE market_forecast
(
    id            VARCHAR(64) PRIMARY KEY,
    symbol        VARCHAR(32) NOT NULL,
    market        VARCHAR(8)  NOT NULL,
    range_key     VARCHAR(8)  NOT NULL,
    name          VARCHAR(100),
    history_json  TEXT        NOT NULL,
    forecast_json TEXT,
    summary       VARCHAR(500),
    job_id        VARCHAR(64),
    status        VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    error_message TEXT,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_market_forecast_created
    ON market_forecast (created_at DESC);

COMMENT ON TABLE market_forecast IS '股市分析一次预测：提交时固化历史K线，任务完成后写入预测点';
COMMENT ON COLUMN market_forecast.id IS '预测记录ID（UUID）';
COMMENT ON COLUMN market_forecast.symbol IS '规范化代码，如 600519 / AAPL';
COMMENT ON COLUMN market_forecast.market IS 'CN 或 US';
COMMENT ON COLUMN market_forecast.range_key IS '时间段：1M / 3M / 6M / 1Y';
COMMENT ON COLUMN market_forecast.name IS '证券简称快照';
COMMENT ON COLUMN market_forecast.history_json IS '提交时的历史K线 JSON';
COMMENT ON COLUMN market_forecast.forecast_json IS 'AI 预测点 JSON，未完成时为空';
COMMENT ON COLUMN market_forecast.summary IS '一两句中文情景摘要';
COMMENT ON COLUMN market_forecast.job_id IS '逻辑关联 async_job.id';
COMMENT ON COLUMN market_forecast.status IS 'PENDING / RUNNING / SUCCEEDED / FAILED';
COMMENT ON COLUMN market_forecast.error_message IS '失败原因';
COMMENT ON COLUMN market_forecast.created_at IS '创建时间';
COMMENT ON COLUMN market_forecast.updated_at IS '更新时间，由应用层维护';
