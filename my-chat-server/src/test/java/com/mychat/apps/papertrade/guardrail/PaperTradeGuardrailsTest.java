package com.mychat.apps.papertrade.guardrail;

import com.mychat.apps.papertrade.research.PaperResearchService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** 搜集卡片领域校验。 */
class PaperTradeGuardrailsTest {

    private final PaperTradeGuardrails guardrails = new PaperTradeGuardrails();

    /** 池外代码剔除；证据不足的 kept 降级。 */
    @Test
    void rejectsOutsidePoolAndWeakEvidence() {
        PaperResearchService.Card ok = new PaperResearchService.Card();
        ok.symbol = "600519";
        ok.kept = true;
        ok.story = "相对低点且有公告支撑";
        ok.fitScore = 0.7;
        ok.evidenceScore = 0.8;

        PaperResearchService.Card weak = new PaperResearchService.Card();
        weak.symbol = "000001";
        weak.kept = true;
        weak.story = "";
        weak.fitScore = 0.9;
        weak.evidenceScore = 0.1;

        PaperResearchService.Card outside = new PaperResearchService.Card();
        outside.symbol = "999999";
        outside.kept = true;
        outside.story = "x";
        outside.fitScore = 0.9;
        outside.evidenceScore = 0.9;

        List<PaperResearchService.Card> out = guardrails.validateCards(
                List.of(ok, weak, outside), Set.of("600519", "000001"));

        assertThat(out).hasSize(2);
        assertThat(out.get(0).kept).isTrue();
        assertThat(out.get(1).kept).isFalse();
        assertThat(out.get(1).rejectReason).contains("证据不足");
    }
}
