package com.mychat.apps.papertrade.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 半截 JSON 抢救单测。 */
class PartialJsonSalvageTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void salvageCardsKeepsCompleteObjectsFromTruncatedBatch() {
        String truncated = """
                {"cards":[
                {"symbol":"600000","name":"浦发","kept":true,"story":"短叙事","risks":["波动"],
                "rejectReason":"","fitScore":0.7,"evidenceScore":0.6,"signalTags":["dip"],
                "evidenceRefs":["公告A"]},
                {"symbol":"600001","name":"半截","kept":false,"story":"未写完
                """;
        ResearchCardBatch batch = PartialJsonSalvage.salvageCards(truncated, mapper);
        assertNotNull(batch);
        assertEquals(1, batch.cards().size());
        assertEquals("600000", batch.cards().get(0).symbol());
        assertTrue(batch.cards().get(0).kept());
    }

    @Test
    void salvageCardsAcceptsObjectEvidenceRefs() {
        String raw = """
                {"cards":[{"symbol":"510300","name":"沪深300","kept":true,"story":"ETF",
                "risks":[],"rejectReason":"","fitScore":0.5,"evidenceScore":0.4,
                "signalTags":[],"evidenceRefs":[{"title":"公告","url":"https://x"}]}]}
                """;
        ResearchCardBatch batch = PartialJsonSalvage.salvageCards(raw, mapper);
        assertNotNull(batch);
        assertEquals(1, batch.cards().size());
        assertEquals("公告", batch.cards().get(0).evidenceRefs().get(0));
    }

    @Test
    void salvageFromNestedFinalOutputEscapedJson() {
        String nested = """
                {"actionId":"","finish":true,"finalOutput":"{\\"cards\\":[{\\"symbol\\":\\"000001\\",\\"name\\":\\"平安\\",\\"kept\\":true,\\"story\\":\\"ok\\",\\"risks\\":[],\\"rejectReason\\":\\"\\",\\"fitScore\\":0.1,\\"evidenceScore\\":0.1,\\"signalTags\\":[],\\"evidenceRefs\\":[\\"a\\"]}],\\"more\\":
                """;
        ResearchCardBatch batch = PartialJsonSalvage.salvageCards(nested, mapper);
        assertNotNull(batch);
        assertEquals("000001", batch.cards().get(0).symbol());
    }
}
