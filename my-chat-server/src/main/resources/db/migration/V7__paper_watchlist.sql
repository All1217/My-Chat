-- 模拟炒股：研究卡片自选 + 交易员是否纳入自选。不改 V1–V6。

SET search_path TO public;

ALTER TABLE paper_research_note
    ADD COLUMN watchlist BOOLEAN NOT NULL DEFAULT FALSE;
COMMENT ON COLUMN paper_research_note.watchlist IS '是否自选：人工新增为是，AI 搜集新建为否';

CREATE INDEX idx_paper_research_note_kept_watchlist_updated
    ON paper_research_note (kept, watchlist, updated_at DESC);

ALTER TABLE paper_schedule_settings
    ADD COLUMN include_watchlist BOOLEAN NOT NULL DEFAULT TRUE;
COMMENT ON COLUMN paper_schedule_settings.include_watchlist IS '是否纳入自选：是则自选优先进入交易员候选，否则交易员看不到自选';
