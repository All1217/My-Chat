package com.mychat.apps.papertrade.research;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 搜集筛选与卡片 JSON。 */
class PaperResearchFilterTest {

    @Test
    void affordableAndLimitHints() {
        assertTrue(PaperResearchFilters.affordable(4.12, new BigDecimal("20000")));
        assertFalse(PaperResearchFilters.affordable(1700d, new BigDecimal("20000")));
        assertTrue(PaperResearchFilters.looksSt("ST 某股"));
        assertTrue(PaperResearchFilters.limitUpHint("600519", 9.9));
        assertFalse(PaperResearchFilters.limitUpHint("300750", 10d));
        LocalDateTime now = LocalDateTime.of(2026, 11, 13, 10, 0);
        assertTrue(PaperResearchFilters.recentlyTouched(now.minusDays(1), 3, now));
        assertFalse(PaperResearchFilters.recentlyTouched(now.minusDays(4), 3, now));
    }

    @Test
    void parsesKeptCardAndPrefersAnswerJson() throws Exception {
        String observations = "[{\"symbol\":\"000001\",\"kept\":false}]";
        String answer = "[{\"symbol\":\"510300\",\"name\":\"沪深300ETF\",\"kept\":true,\"story\":\"低于均线\",\"risks\":\"趋势\",\"rejectReason\":null}]";
        assertEquals(answer, PaperResearchService.firstJson(answer, observations));
        List<PaperResearchService.Card> cards = PaperResearchService.parseCards(answer);
        assertEquals(1, cards.size());
        assertEquals("510300", cards.get(0).symbol);
        assertTrue(cards.get(0).kept);
    }

    @Test
    void keepsFirstCardWhenSymbolRepeats() {
        PaperResearchService.Card first = new PaperResearchService.Card();
        first.symbol = "601138";
        first.kept = true;
        first.story = "员工持股";
        PaperResearchService.Card second = new PaperResearchService.Card();
        second.symbol = " 601138 ";
        second.kept = false;
        second.story = "证据不足";
        List<PaperResearchService.Card> cards = PaperResearchService.firstCards(List.of(first, second));
        assertEquals(1, cards.size());
        assertTrue(cards.get(0).kept);
        assertEquals("员工持股", cards.get(0).story);
    }

    @Test
    void failsWhenEveryQuoteInTheBatchDies() {
        assertThrows(IllegalStateException.class, () -> PaperResearchService.requireAnyQuote(15, 15));
        assertDoesNotThrow(() -> PaperResearchService.requireAnyQuote(15, 3));
        assertDoesNotThrow(() -> PaperResearchService.requireAnyQuote(0, 0));
    }
}
