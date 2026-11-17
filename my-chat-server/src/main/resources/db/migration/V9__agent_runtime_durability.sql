-- 通用 Agent Run：保存语义执行状态与安全 checkpoint；async_job 仍只负责调度和通知。
CREATE TABLE agent_run (
    id                 VARCHAR(64) PRIMARY KEY,
    scenario           VARCHAR(64) NOT NULL,
    profile_version    VARCHAR(64) NOT NULL,
    business_ref       VARCHAR(128),
    conversation_id    VARCHAR(255),
    user_id            VARCHAR(64),
    async_job_id       VARCHAR(64),
    idempotency_key    VARCHAR(128),
    status             VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    current_step       INTEGER NOT NULL DEFAULT 0,
    checkpoint_version INTEGER NOT NULL DEFAULT 0,
    checkpoint         JSONB NOT NULL DEFAULT '{}'::jsonb,
    input_summary      TEXT,
    output_summary     TEXT,
    model_name         VARCHAR(128),
    input_tokens       BIGINT NOT NULL DEFAULT 0,
    output_tokens      BIGINT NOT NULL DEFAULT 0,
    total_tokens       BIGINT NOT NULL DEFAULT 0,
    error_message      TEXT,
    started_at         TIMESTAMP,
    completed_at       TIMESTAMP,
    created_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_agent_run_status CHECK (
        status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED',
                   'PAUSED', 'WAITING_APPROVAL', 'BUDGET_EXHAUSTED')
    ),
    CONSTRAINT ck_agent_run_current_step CHECK (current_step >= 0),
    CONSTRAINT ck_agent_run_checkpoint_version CHECK (checkpoint_version >= 0),
    CONSTRAINT ck_agent_run_tokens CHECK (
        input_tokens >= 0 AND output_tokens >= 0 AND total_tokens >= 0
    )
);

COMMENT ON TABLE agent_run IS '通用 Agent 语义运行记录；checkpoint 只在安全边界推进';
COMMENT ON COLUMN agent_run.async_job_id IS '可选 async_job 关联；job 管调度通知，run 管语义执行';
COMMENT ON COLUMN agent_run.checkpoint IS '可恢复状态的脱敏 JSON，不保存系统提示或完整工具结果';
COMMENT ON COLUMN agent_run.idempotency_key IS '调用方提供的 Run 级幂等键';

CREATE INDEX idx_agent_run_idempotency
    ON agent_run (scenario, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
CREATE INDEX idx_agent_run_status_created
    ON agent_run (status, created_at DESC);
CREATE INDEX idx_agent_run_scenario_created
    ON agent_run (scenario, created_at DESC);
CREATE INDEX idx_agent_run_business_ref
    ON agent_run (business_ref, created_at DESC)
    WHERE business_ref IS NOT NULL;
CREATE INDEX idx_agent_run_conversation
    ON agent_run (conversation_id, created_at DESC)
    WHERE conversation_id IS NOT NULL;

-- Agent Span/Step：统一记录 workflow/action/model/tool/guardrail/handoff/evaluator/approval。
CREATE TABLE agent_run_step (
    id                VARCHAR(64) PRIMARY KEY,
    run_id            VARCHAR(64) NOT NULL REFERENCES agent_run(id) ON DELETE CASCADE,
    sequence_no       BIGSERIAL NOT NULL,
    checkpoint_index  INTEGER,
    parent_step_id    VARCHAR(64) REFERENCES agent_run_step(id) ON DELETE SET NULL,
    step_type         VARCHAR(32) NOT NULL,
    action_id         VARCHAR(128),
    tool_name         VARCHAR(128),
    display_name      VARCHAR(200),
    status            VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    idempotency_key   VARCHAR(128),
    input_summary     TEXT,
    output_summary    TEXT,
    citations         JSONB NOT NULL DEFAULT '[]'::jsonb,
    metadata          JSONB NOT NULL DEFAULT '{}'::jsonb,
    input_tokens      BIGINT NOT NULL DEFAULT 0,
    output_tokens     BIGINT NOT NULL DEFAULT 0,
    elapsed_ms        BIGINT NOT NULL DEFAULT 0,
    error_message     TEXT,
    started_at        TIMESTAMP,
    completed_at      TIMESTAMP,
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_agent_run_step_type CHECK (
        step_type IN ('WORKFLOW', 'ACTION', 'MODEL', 'TOOL', 'GUARDRAIL',
                      'HANDOFF', 'EVALUATOR', 'APPROVAL')
    ),
    CONSTRAINT ck_agent_run_step_status CHECK (
        status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'SKIPPED',
                   'WAITING_APPROVAL', 'CANCELLED')
    ),
    CONSTRAINT ck_agent_run_step_checkpoint CHECK (
        checkpoint_index IS NULL OR checkpoint_index >= 1
    ),
    CONSTRAINT ck_agent_run_step_metrics CHECK (
        input_tokens >= 0 AND output_tokens >= 0 AND elapsed_ms >= 0
    )
);

COMMENT ON TABLE agent_run_step IS 'Agent 内部 Trace Span；公开时间线必须经 PublicProgressAdapter 脱敏';
COMMENT ON COLUMN agent_run_step.idempotency_key IS '写动作稳定幂等键；同一 Run 内不可重复';
COMMENT ON COLUMN agent_run_step.output_summary IS '脱敏且截断后的摘要，禁止保存完整敏感工具结果';

CREATE UNIQUE INDEX uq_agent_run_step_idempotency
    ON agent_run_step (run_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
CREATE INDEX idx_agent_run_step_run_sequence
    ON agent_run_step (run_id, sequence_no);
CREATE UNIQUE INDEX uq_agent_run_step_checkpoint
    ON agent_run_step (run_id, checkpoint_index)
    WHERE checkpoint_index IS NOT NULL AND step_type = 'ACTION';
CREATE INDEX idx_agent_run_step_parent
    ON agent_run_step (parent_step_id)
    WHERE parent_step_id IS NOT NULL;
CREATE INDEX idx_agent_run_step_status
    ON agent_run_step (status, created_at DESC);

-- 高风险动作审批：请求与决定分离，可在恢复时核对批准状态。
CREATE TABLE agent_approval (
    id                VARCHAR(64) PRIMARY KEY,
    run_id            VARCHAR(64) NOT NULL REFERENCES agent_run(id) ON DELETE CASCADE,
    step_id           VARCHAR(64) NOT NULL REFERENCES agent_run_step(id) ON DELETE CASCADE,
    action_id         VARCHAR(128) NOT NULL,
    idempotency_key   VARCHAR(128) NOT NULL,
    status            VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    request_summary   TEXT,
    decision_note     TEXT,
    requested_by      VARCHAR(64),
    decided_by        VARCHAR(64),
    requested_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    decided_at        TIMESTAMP,
    expires_at        TIMESTAMP,
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_agent_approval_step UNIQUE (run_id, step_id),
    CONSTRAINT uq_agent_approval_idempotency UNIQUE (run_id, idempotency_key),
    CONSTRAINT ck_agent_approval_status CHECK (
        status IN ('PENDING', 'APPROVED', 'REJECTED', 'EXPIRED', 'CANCELLED')
    )
);

COMMENT ON TABLE agent_approval IS '高风险 Agent 动作审批记录';
COMMENT ON COLUMN agent_approval.request_summary IS '面向审批人的脱敏动作摘要';

CREATE INDEX idx_agent_approval_run_status
    ON agent_approval (run_id, status);
CREATE INDEX idx_agent_approval_pending
    ON agent_approval (requested_at)
    WHERE status = 'PENDING';
