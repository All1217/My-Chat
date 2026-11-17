-- 策略半可编辑：custom_content + 结构化风控单行表。不改 V1–V12。

SET search_path TO public;

ALTER TABLE paper_strategy
    ADD COLUMN IF NOT EXISTS custom_content TEXT;

COMMENT ON COLUMN paper_strategy.custom_content IS '用户可编辑风格/偏好；content 为锁定区+本字段拼接';

CREATE TABLE IF NOT EXISTS paper_risk_settings
(
    id                         VARCHAR(64) PRIMARY KEY,
    max_positions              INTEGER          NOT NULL DEFAULT 2,
    max_single_allocation      DOUBLE PRECISION NOT NULL DEFAULT 0.95,
    risk_budget_pct            DOUBLE PRECISION NOT NULL DEFAULT 0.02,
    risk_budget_tight_pct      DOUBLE PRECISION NOT NULL DEFAULT 0.01,
    drawdown_restrict_pct      DOUBLE PRECISION NOT NULL DEFAULT 0.08,
    loss_streak_limit          INTEGER          NOT NULL DEFAULT 3,
    buy_pause_days             INTEGER          NOT NULL DEFAULT 2,
    review_confidence_threshold DOUBLE PRECISION NOT NULL DEFAULT 0.55,
    auto_execute               BOOLEAN          NOT NULL DEFAULT TRUE,
    cooldown_days_stock        INTEGER          NOT NULL DEFAULT 2,
    cooldown_days_etf          INTEGER          NOT NULL DEFAULT 1,
    stock_take_profit_pct      DOUBLE PRECISION NOT NULL DEFAULT 0.04,
    stock_stop_loss_pct        DOUBLE PRECISION NOT NULL DEFAULT 0.03,
    stock_max_hold_days        INTEGER          NOT NULL DEFAULT 10,
    etf_take_profit_pct        DOUBLE PRECISION NOT NULL DEFAULT 0.03,
    etf_stop_loss_pct          DOUBLE PRECISION NOT NULL DEFAULT 0.02,
    etf_max_hold_days          INTEGER          NOT NULL DEFAULT 15,
    qdii_take_profit_pct       DOUBLE PRECISION NOT NULL DEFAULT 0.045,
    qdii_stop_loss_pct         DOUBLE PRECISION NOT NULL DEFAULT 0.03,
    qdii_max_hold_days         INTEGER          NOT NULL DEFAULT 10,
    updated_at                 TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE paper_risk_settings IS '模拟炒股运行时风控（单行）；闸门与锁定策略文案共用';

INSERT INTO paper_risk_settings (id)
VALUES ('default')
ON CONFLICT (id) DO NOTHING;
