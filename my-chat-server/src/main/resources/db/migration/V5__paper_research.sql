-- My-Chat 模拟炒股：信息搜集卡片与编排轨迹。不改 V1–V4。

SET search_path TO public;

ALTER TABLE paper_ai_run
    ADD COLUMN framework_trace TEXT;
COMMENT ON COLUMN paper_ai_run.framework_trace IS 'Orchestrator 步骤、结束原因与耗时';

CREATE TABLE paper_research_run
(
    id               VARCHAR(64) PRIMARY KEY,
    job_id           VARCHAR(64),
    status           VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    screened_count   INTEGER     NOT NULL DEFAULT 0,
    kept_count       INTEGER     NOT NULL DEFAULT 0,
    framework_trace  TEXT,
    error_message    TEXT,
    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    finished_at      TIMESTAMP
);
CREATE INDEX idx_paper_research_run_created ON paper_research_run (created_at DESC);
COMMENT ON TABLE paper_research_run IS '信息搜集一轮；只写卡片，不下单';
COMMENT ON COLUMN paper_research_run.id IS '搜集轮次ID';
COMMENT ON COLUMN paper_research_run.job_id IS '逻辑关联 async_job.id';
COMMENT ON COLUMN paper_research_run.status IS 'PENDING / RUNNING / SUCCEEDED / FAILED';
COMMENT ON COLUMN paper_research_run.screened_count IS '本批交给模型的标的数';
COMMENT ON COLUMN paper_research_run.kept_count IS '本批新留下的卡片数';
COMMENT ON COLUMN paper_research_run.framework_trace IS 'Orchestrator 步骤、结束原因与耗时';
COMMENT ON COLUMN paper_research_run.error_message IS '失败原因';
COMMENT ON COLUMN paper_research_run.created_at IS '创建时间';
COMMENT ON COLUMN paper_research_run.finished_at IS '结束时间';

CREATE TABLE paper_research_note
(
    symbol         VARCHAR(32) PRIMARY KEY,
    name           VARCHAR(100),
    quote_summary  TEXT,
    story          TEXT,
    risks          TEXT,
    kept           BOOLEAN     NOT NULL DEFAULT FALSE,
    reject_reason  VARCHAR(500),
    sources_json   TEXT,
    run_id         VARCHAR(64),
    created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_paper_research_note_kept_updated
    ON paper_research_note (kept, updated_at DESC);
COMMENT ON TABLE paper_research_note IS '按代码累积的研究卡片，操盘手只读 kept=true';
COMMENT ON COLUMN paper_research_note.symbol IS '规范化代码，一代码一行';
COMMENT ON COLUMN paper_research_note.name IS '名称';
COMMENT ON COLUMN paper_research_note.quote_summary IS '最近一轮行情摘要 JSON';
COMMENT ON COLUMN paper_research_note.story IS '搜集员写下的故事';
COMMENT ON COLUMN paper_research_note.risks IS '风险说明';
COMMENT ON COLUMN paper_research_note.kept IS '是否留给操盘手';
COMMENT ON COLUMN paper_research_note.reject_reason IS '不跟踪的原因';
COMMENT ON COLUMN paper_research_note.sources_json IS '公告与新闻标题、链接、时间';
COMMENT ON COLUMN paper_research_note.run_id IS '最近一次写入的搜集轮次';
COMMENT ON COLUMN paper_research_note.created_at IS '首次入库时间';
COMMENT ON COLUMN paper_research_note.updated_at IS '最近整理时间';
