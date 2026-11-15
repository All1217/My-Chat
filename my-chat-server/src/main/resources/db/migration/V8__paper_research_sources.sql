-- 搜集信源扩展：工具检索开关 + 研究卡片评分字段。不改 V1–V7。

SET search_path TO public;

ALTER TABLE paper_schedule_settings
    ADD COLUMN IF NOT EXISTS research_tool_search_enabled BOOLEAN NOT NULL DEFAULT FALSE;
COMMENT ON COLUMN paper_schedule_settings.research_tool_search_enabled IS
    '是否启用工具检索（Exa/Bocha/Tavily）；默认关，仅 A 股 HTTP 信源';

ALTER TABLE paper_research_note
    ADD COLUMN IF NOT EXISTS fit_score NUMERIC(10, 4);
ALTER TABLE paper_research_note
    ADD COLUMN IF NOT EXISTS evidence_score NUMERIC(10, 4);
ALTER TABLE paper_research_note
    ADD COLUMN IF NOT EXISTS signal_tags TEXT;
ALTER TABLE paper_research_note
    ADD COLUMN IF NOT EXISTS source_tier VARCHAR(32);

COMMENT ON COLUMN paper_research_note.fit_score IS '与投资策略契合度 0-1';
COMMENT ON COLUMN paper_research_note.evidence_score IS '证据充分度 0-1';
COMMENT ON COLUMN paper_research_note.signal_tags IS '信号标签 JSON 数组字符串';
COMMENT ON COLUMN paper_research_note.source_tier IS 'A_ONLY / A_PLUS_WEB';
