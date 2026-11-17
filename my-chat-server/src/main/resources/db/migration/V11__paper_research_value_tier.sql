-- 研究卡片价值等级：0已持仓 1应该买入 2继续观察 3移出票仓。不改 V1–V10。

SET search_path TO public;

ALTER TABLE paper_research_note
    ADD COLUMN value_tier SMALLINT NOT NULL DEFAULT 2;

COMMENT ON COLUMN paper_research_note.value_tier IS
    '价值等级：0已持仓 1应该买入 2继续观察 3移出票仓；数字越小价值越高';

CREATE INDEX idx_paper_research_note_value_tier_kept
    ON paper_research_note (value_tier, kept, updated_at DESC);

-- 历史回填：未留下 → 3；已留下保持默认 2（持仓对齐由成交回写或运行时校准）
UPDATE paper_research_note
SET value_tier = 3
WHERE kept = FALSE;
