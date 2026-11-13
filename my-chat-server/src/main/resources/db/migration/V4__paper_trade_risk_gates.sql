-- 模拟炒股风控：阶段高点、连亏暂停、卖出盈亏、阶段总结。

SET search_path TO public;

ALTER TABLE paper_account
    ADD COLUMN peak_equity NUMERIC(18, 4),
    ADD COLUMN max_drawdown NUMERIC(10, 6) NOT NULL DEFAULT 0,
    ADD COLUMN loss_streak INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN buy_pause_until DATE,
    ADD COLUMN stage_started_at TIMESTAMP;

COMMENT ON COLUMN paper_account.peak_equity IS '本阶段权益高点';
COMMENT ON COLUMN paper_account.max_drawdown IS '本阶段最大回撤比例';
COMMENT ON COLUMN paper_account.loss_streak IS '连续止损笔数';
COMMENT ON COLUMN paper_account.buy_pause_until IS '暂停新开仓截止日（含）';
COMMENT ON COLUMN paper_account.stage_started_at IS '本阶段开始时间';

ALTER TABLE paper_order
    ADD COLUMN realized_pnl NUMERIC(18, 4),
    ADD COLUMN exit_reason VARCHAR(32);

COMMENT ON COLUMN paper_order.realized_pnl IS '卖出实现盈亏（含本笔费用）';
COMMENT ON COLUMN paper_order.exit_reason IS 'STOP_LOSS / TAKE_PROFIT / MAX_HOLD / AI / MANUAL';

CREATE TABLE paper_stage_summary
(
    id            VARCHAR(64) PRIMARY KEY,
    stage_no      INTEGER        NOT NULL,
    started_at    TIMESTAMP,
    ended_at      TIMESTAMP      NOT NULL,
    start_cash    NUMERIC(18, 4) NOT NULL,
    end_equity    NUMERIC(18, 4) NOT NULL,
    trade_count   INTEGER        NOT NULL,
    win_rate      NUMERIC(10, 6),
    payoff_ratio  NUMERIC(10, 6),
    max_drawdown  NUMERIC(10, 6),
    avg_hold_days NUMERIC(10, 2),
    total_fee     NUMERIC(18, 4) NOT NULL,
    loss_sources  TEXT,
    summary_text  TEXT           NOT NULL,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE paper_stage_summary IS '模拟盘阶段重置前的成绩总结';
COMMENT ON COLUMN paper_stage_summary.id IS '总结ID';
COMMENT ON COLUMN paper_stage_summary.stage_no IS '阶段序号，从 1 递增';
COMMENT ON COLUMN paper_stage_summary.started_at IS '阶段开始时间';
COMMENT ON COLUMN paper_stage_summary.ended_at IS '阶段结束时间';
COMMENT ON COLUMN paper_stage_summary.start_cash IS '阶段起始资金';
COMMENT ON COLUMN paper_stage_summary.end_equity IS '重置前权益';
COMMENT ON COLUMN paper_stage_summary.trade_count IS '已平仓卖出笔数';
COMMENT ON COLUMN paper_stage_summary.win_rate IS '盈利卖出占比';
COMMENT ON COLUMN paper_stage_summary.payoff_ratio IS '平均盈利 / 平均亏损绝对值';
COMMENT ON COLUMN paper_stage_summary.max_drawdown IS '阶段最大回撤比例';
COMMENT ON COLUMN paper_stage_summary.avg_hold_days IS '平均持有工作日';
COMMENT ON COLUMN paper_stage_summary.total_fee IS '佣金与印花税合计';
COMMENT ON COLUMN paper_stage_summary.loss_sources IS '出场原因计数';
COMMENT ON COLUMN paper_stage_summary.summary_text IS '给下一阶段模型看的总结';
COMMENT ON COLUMN paper_stage_summary.created_at IS '写入时间';

-- 已有账户补上阶段起点，不改现金。
UPDATE paper_account
SET peak_equity = cash,
    stage_started_at = COALESCE(created_at, CURRENT_TIMESTAMP)
WHERE peak_equity IS NULL;

-- 仍是 100 万种子、且没有持仓和订单的 default 账户，收到 2 万。
UPDATE paper_account a
SET initial_cash = 20000,
    cash = 20000,
    peak_equity = 20000,
    max_drawdown = 0,
    loss_streak = 0,
    buy_pause_until = NULL,
    stage_started_at = CURRENT_TIMESTAMP,
    updated_at = CURRENT_TIMESTAMP
WHERE a.id = 'default'
  AND a.initial_cash = 1000000
  AND a.cash = 1000000
  AND NOT EXISTS (SELECT 1 FROM paper_position p WHERE p.account_id = a.id)
  AND NOT EXISTS (SELECT 1 FROM paper_order o WHERE o.account_id = a.id);
