-- My-Chat 模拟炒股结构
-- Flyway V3：默认账户和策略由应用 SeedRunner 写入。

SET search_path TO public;

CREATE TABLE paper_account
(
    id           VARCHAR(64) PRIMARY KEY,
    name         VARCHAR(100)   NOT NULL,
    cash         NUMERIC(18, 4) NOT NULL,
    initial_cash NUMERIC(18, 4) NOT NULL,
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE paper_account IS '模拟炒股虚拟账户（非实盘）';
COMMENT ON COLUMN paper_account.id IS '账户ID（默认 seed 为 default）';
COMMENT ON COLUMN paper_account.name IS '账户显示名';
COMMENT ON COLUMN paper_account.cash IS '可用现金';
COMMENT ON COLUMN paper_account.initial_cash IS '初始资金，重置时回写到 cash';
COMMENT ON COLUMN paper_account.created_at IS '创建时间';
COMMENT ON COLUMN paper_account.updated_at IS '更新时间，由应用层维护';

CREATE TABLE paper_position
(
    id              VARCHAR(64) PRIMARY KEY,
    account_id      VARCHAR(64)    NOT NULL,
    symbol          VARCHAR(32)    NOT NULL,
    market          VARCHAR(8)     NOT NULL,
    qty             INTEGER        NOT NULL,
    available_qty   INTEGER        NOT NULL DEFAULT 0,
    avg_cost        NUMERIC(18, 4) NOT NULL,
    opened_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    take_profit_pct NUMERIC(10, 6),
    stop_loss_pct   NUMERIC(10, 6),
    max_hold_days   INTEGER,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_paper_position_account_symbol UNIQUE (account_id, symbol)
);
CREATE INDEX idx_paper_position_account ON paper_position (account_id);
COMMENT ON TABLE paper_position IS '模拟炒股持仓';
COMMENT ON COLUMN paper_position.id IS '持仓行ID';
COMMENT ON COLUMN paper_position.account_id IS '所属模拟账户';
COMMENT ON COLUMN paper_position.symbol IS '规范化代码，如 600519 / AAPL';
COMMENT ON COLUMN paper_position.market IS 'CN 或 US';
COMMENT ON COLUMN paper_position.qty IS '持股数量（股）';
COMMENT ON COLUMN paper_position.available_qty IS '可卖数量（T+1：当日买入为 0）';
COMMENT ON COLUMN paper_position.avg_cost IS '持仓成本均价';
COMMENT ON COLUMN paper_position.opened_at IS '最近建仓/加仓时间，用于最长持有天数';
COMMENT ON COLUMN paper_position.take_profit_pct IS '建仓时记录的止盈比例（如 0.04）';
COMMENT ON COLUMN paper_position.stop_loss_pct IS '建仓时记录的止损比例';
COMMENT ON COLUMN paper_position.max_hold_days IS '最长持有交易日数';
COMMENT ON COLUMN paper_position.updated_at IS '更新时间';

CREATE TABLE paper_order
(
    id            VARCHAR(64) PRIMARY KEY,
    account_id    VARCHAR(64) NOT NULL,
    side          VARCHAR(8)  NOT NULL,
    symbol        VARCHAR(32) NOT NULL,
    market        VARCHAR(8)  NOT NULL,
    qty           INTEGER     NOT NULL,
    fill_price    NUMERIC(18, 4),
    fee           NUMERIC(18, 4) DEFAULT 0,
    source        VARCHAR(16) NOT NULL DEFAULT 'MANUAL',
    decision_id   VARCHAR(64),
    status        VARCHAR(20) NOT NULL,
    reject_reason VARCHAR(500),
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_paper_order_account_created
    ON paper_order (account_id, created_at DESC);
COMMENT ON TABLE paper_order IS '模拟炒股市价单（成交或拒单）';
COMMENT ON COLUMN paper_order.id IS '订单ID';
COMMENT ON COLUMN paper_order.account_id IS '所属模拟账户';
COMMENT ON COLUMN paper_order.side IS 'BUY 或 SELL';
COMMENT ON COLUMN paper_order.symbol IS '规范化代码';
COMMENT ON COLUMN paper_order.market IS 'CN 或 US';
COMMENT ON COLUMN paper_order.qty IS '委托数量（股）';
COMMENT ON COLUMN paper_order.fill_price IS '成交价（东财最新收盘）；拒单为空';
COMMENT ON COLUMN paper_order.fee IS '佣金+印花税等合计费用';
COMMENT ON COLUMN paper_order.source IS 'MANUAL / AI / EXIT';
COMMENT ON COLUMN paper_order.decision_id IS '逻辑关联 paper_ai_decision.id';
COMMENT ON COLUMN paper_order.status IS 'FILLED / REJECTED';
COMMENT ON COLUMN paper_order.reject_reason IS '拒单原因';
COMMENT ON COLUMN paper_order.created_at IS '下单时间';

CREATE TABLE paper_cooldown
(
    account_id VARCHAR(64) NOT NULL,
    symbol     VARCHAR(32) NOT NULL,
    until_date DATE        NOT NULL,
    PRIMARY KEY (account_id, symbol)
);
COMMENT ON TABLE paper_cooldown IS '卖出后冷却期，到期前不可再买该标的';
COMMENT ON COLUMN paper_cooldown.account_id IS '模拟账户';
COMMENT ON COLUMN paper_cooldown.symbol IS '标的代码';
COMMENT ON COLUMN paper_cooldown.until_date IS '冷却截止日（含当日不可买）';

CREATE TABLE paper_strategy
(
    id         VARCHAR(64) PRIMARY KEY,
    content    TEXT NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE paper_strategy IS '模拟炒股 AI 策略正文（系统提示）';
COMMENT ON COLUMN paper_strategy.id IS '策略ID，默认 default';
COMMENT ON COLUMN paper_strategy.content IS '完整交易员策略提示词';
COMMENT ON COLUMN paper_strategy.updated_at IS '更新时间';

CREATE TABLE paper_ai_run
(
    id            VARCHAR(64) PRIMARY KEY,
    job_id        VARCHAR(64),
    status        VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    context_json  TEXT,
    raw_response  TEXT,
    error_message TEXT,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    finished_at   TIMESTAMP
);
CREATE INDEX idx_paper_ai_run_created ON paper_ai_run (created_at DESC);
COMMENT ON TABLE paper_ai_run IS 'AI 交易一轮运行记录';
COMMENT ON COLUMN paper_ai_run.id IS '运行ID';
COMMENT ON COLUMN paper_ai_run.job_id IS '逻辑关联 async_job.id';
COMMENT ON COLUMN paper_ai_run.status IS 'PENDING / RUNNING / SUCCEEDED / FAILED';
COMMENT ON COLUMN paper_ai_run.context_json IS '喂给模型的上下文快照';
COMMENT ON COLUMN paper_ai_run.raw_response IS '模型原始输出';
COMMENT ON COLUMN paper_ai_run.error_message IS '失败原因';
COMMENT ON COLUMN paper_ai_run.created_at IS '创建时间';
COMMENT ON COLUMN paper_ai_run.finished_at IS '结束时间';

CREATE TABLE paper_ai_decision
(
    id                 VARCHAR(64) PRIMARY KEY,
    run_id             VARCHAR(64) NOT NULL,
    action             VARCHAR(16) NOT NULL,
    symbol             VARCHAR(32),
    name               VARCHAR(100),
    qty                INTEGER,
    ref_price          NUMERIC(18, 4),
    take_profit_pct    NUMERIC(10, 6),
    stop_loss_pct      NUMERIC(10, 6),
    max_hold_days      INTEGER,
    cooldown_days      INTEGER,
    confidence         NUMERIC(10, 6),
    rationale          TEXT,
    risks_json         TEXT,
    needs_human_review BOOLEAN     NOT NULL DEFAULT FALSE,
    exec_status        VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    skip_reason        VARCHAR(500),
    order_id           VARCHAR(64),
    created_at         TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_paper_ai_decision_run ON paper_ai_decision (run_id);
CREATE INDEX idx_paper_ai_decision_exec
    ON paper_ai_decision (exec_status, created_at DESC);
COMMENT ON TABLE paper_ai_decision IS 'AI 结构化交易决策';
COMMENT ON COLUMN paper_ai_decision.id IS '决策ID';
COMMENT ON COLUMN paper_ai_decision.run_id IS '所属 AI 轮次';
COMMENT ON COLUMN paper_ai_decision.action IS 'BUY / SELL / HOLD / FLAT';
COMMENT ON COLUMN paper_ai_decision.symbol IS '标的代码';
COMMENT ON COLUMN paper_ai_decision.name IS '标的名称';
COMMENT ON COLUMN paper_ai_decision.qty IS '计划股数';
COMMENT ON COLUMN paper_ai_decision.ref_price IS '参考价';
COMMENT ON COLUMN paper_ai_decision.take_profit_pct IS '止盈比例';
COMMENT ON COLUMN paper_ai_decision.stop_loss_pct IS '止损比例';
COMMENT ON COLUMN paper_ai_decision.max_hold_days IS '最长持有天数';
COMMENT ON COLUMN paper_ai_decision.cooldown_days IS '卖出后冷却天数';
COMMENT ON COLUMN paper_ai_decision.confidence IS '置信度 0-1';
COMMENT ON COLUMN paper_ai_decision.rationale IS '决策理由';
COMMENT ON COLUMN paper_ai_decision.risks_json IS '风险点 JSON 数组';
COMMENT ON COLUMN paper_ai_decision.needs_human_review IS '是否需人工复核';
COMMENT ON COLUMN paper_ai_decision.exec_status IS 'PENDING / EXECUTED / SKIPPED / REJECTED / DISMISSED';
COMMENT ON COLUMN paper_ai_decision.skip_reason IS '跳过/拒绝原因';
COMMENT ON COLUMN paper_ai_decision.order_id IS '成交后逻辑关联 paper_order.id';
COMMENT ON COLUMN paper_ai_decision.created_at IS '创建时间';
