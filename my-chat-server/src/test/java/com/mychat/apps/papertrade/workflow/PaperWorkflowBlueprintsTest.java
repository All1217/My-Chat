package com.mychat.apps.papertrade.workflow;

import com.mychat.apps.papertrade.workflow.entity.vo.PaperWorkflowGraphVO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 交易评估员蓝图冒烟。 */
class PaperWorkflowBlueprintsTest {

    @Test
    void tradeEvaluatorHasFiveDomainStepsPlusFinish() {
        PaperWorkflowGraphVO g = PaperWorkflowBlueprints.tradeEvaluator();
        assertEquals("trade-evaluator", g.getId());
        assertEquals("交易评估员", g.getTitle());
        assertEquals(6, g.getNodes().size());
        assertTrue(g.getNodes().stream().anyMatch(n ->
                PaperWorkflowStep.GATHER_EVIDENCE.name().equals(n.getId())));
        assertTrue(g.getNodes().stream().anyMatch(n ->
                PaperWorkflowStep.AI_EVALUATE.name().equals(n.getId())));
        assertTrue(g.getNodes().stream().anyMatch(n ->
                PaperWorkflowStep.WRITE_STAGE_SUMMARY.name().equals(n.getId())));
        assertTrue(g.getNodes().stream().anyMatch(n ->
                PaperWorkflowStep.SEND_EMAIL.name().equals(n.getId())));
    }
}
