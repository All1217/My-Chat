package com.mychat.service.agent.workflow;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * kbScope 规范化：仅 retrieve_kb 有效，非法默认 vector。
 */
class OrchestratorWorkflowTest {

    /** retrieve_kb + catalog → catalog。 */
    @Test
    void retrieveKbCatalogKept() {
        assertEquals("catalog", OrchestratorWorkflow.normalizeKbScope("retrieve_kb", "catalog"));
        assertEquals("catalog", OrchestratorWorkflow.normalizeKbScope("retrieve_kb", " CATALOG "));
    }

    /** retrieve_kb 空/非法 → vector。 */
    @Test
    void retrieveKbMissingOrInvalidDefaultsVector() {
        assertEquals("vector", OrchestratorWorkflow.normalizeKbScope("retrieve_kb", null));
        assertEquals("vector", OrchestratorWorkflow.normalizeKbScope("retrieve_kb", " "));
        assertEquals("vector", OrchestratorWorkflow.normalizeKbScope("retrieve_kb", "overview"));
        assertEquals("vector", OrchestratorWorkflow.normalizeKbScope("retrieve_kb", "vector"));
    }

    /** 非 retrieve_kb → 空。 */
    @Test
    void nonRetrieveKbClearsScope() {
        assertNull(OrchestratorWorkflow.normalizeKbScope("finish", "catalog"));
        assertNull(OrchestratorWorkflow.normalizeKbScope("general", "vector"));
        assertNull(OrchestratorWorkflow.normalizeKbScope("file", null));
    }
}
