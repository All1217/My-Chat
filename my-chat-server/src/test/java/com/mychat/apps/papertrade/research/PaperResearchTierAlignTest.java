package com.mychat.apps.papertrade.research;

import com.mychat.apps.market.MarketDipIndicators;
import com.mychat.apps.papertrade.risk.PaperSymbolFacts;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** tier=1 与相对低点/企稳闸门对齐。 */
class PaperResearchTierAlignTest {

    @Test
    void demotesTier1WhenNotStabilized() {
        PaperResearchService.Card card = tier1Card("600000");
        PaperResearchService.Material material = material(
                "600000",
                snap(-3.0, -10.0),
                facts(false));
        PaperResearchService.alignTier1WithStabilizeGates(List.of(card), List.of(material));
        assertEquals(2, card.valueTier);
        assertTrue(card.rejectReason.contains("TIER1_WITHOUT_STABILIZED"));
    }

    @Test
    void demotesTier1WhenNotRelativeLow() {
        PaperResearchService.Card card = tier1Card("600001");
        PaperResearchService.Material material = material(
                "600001",
                snap(2.5, -3.0),
                facts(true));
        PaperResearchService.alignTier1WithStabilizeGates(List.of(card), List.of(material));
        assertEquals(2, card.valueTier);
        assertTrue(card.rejectReason.contains("TIER1_WITHOUT_RELATIVE_LOW"));
    }

    @Test
    void keepsTier1WhenDipAndStabilized() {
        PaperResearchService.Card card = tier1Card("600002");
        PaperResearchService.Material material = material(
                "600002",
                snap(-1.2, -9.0),
                facts(true));
        PaperResearchService.alignTier1WithStabilizeGates(List.of(card), List.of(material));
        assertEquals(1, card.valueTier);
    }

    @Test
    void relativelyLowMatchesMaOrDrawdown() {
        assertTrue(PaperResearchService.relativelyLow(snap(-0.1, null)));
        assertTrue(PaperResearchService.relativelyLow(snap(1.0, -8.0)));
        assertEquals(false, PaperResearchService.relativelyLow(snap(1.0, -7.9)));
    }

    private static PaperResearchService.Card tier1Card(String symbol) {
        PaperResearchService.Card card = new PaperResearchService.Card();
        card.symbol = symbol;
        card.kept = true;
        card.valueTier = 1;
        card.story = "低吸";
        card.fitScore = 0.8;
        card.evidenceScore = 0.8;
        return card;
    }

    private static PaperResearchService.Material material(
            String symbol, MarketDipIndicators.Snapshot snap, PaperSymbolFacts facts) {
        return new PaperResearchService.Material(
                symbol, symbol, snap, facts, null, List.of(), "A_ONLY");
    }

    private static MarketDipIndicators.Snapshot snap(Double vsMa20, Double dd60) {
        return new MarketDipIndicators.Snapshot(
                "x", "x", 10.0, 0.0, null, dd60, vsMa20, null, null, null, null, null);
    }

    private static PaperSymbolFacts facts(boolean stabilized) {
        return new PaperSymbolFacts(
                "x", "x", false, false, false, false, false, false, stabilized, 100, 1e9);
    }
}
