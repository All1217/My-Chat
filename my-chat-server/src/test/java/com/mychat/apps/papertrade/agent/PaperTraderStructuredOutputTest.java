package com.mychat.apps.papertrade.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mychat.agent.api.AgentRunResult;
import com.mychat.agent.api.AgentStepResult;
import com.mychat.agent.runtime.ScenarioAgentService;
import com.mychat.agent.runtime.StructuredDecisionEngine;
import com.mychat.agent.runtime.StructuredOutputRepairService;
import com.mychat.apps.papertrade.trader.entity.po.PaperAiDecision;
import com.mychat.apps.papertrade.trader.mapper.PaperAiDecisionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** 交易员结束门槛与已落库决策兜底。 */
@ExtendWith(MockitoExtension.class)
class PaperTraderStructuredOutputTest {

    @Mock
    private ScenarioAgentService scenarioAgentService;
    @Mock
    private StructuredOutputRepairService repairService;
    @Mock
    private PaperAiDecisionMapper decisionMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private PaperAgentRuntimeAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new PaperAgentRuntimeAdapter(
                scenarioAgentService, repairService, objectMapper, decisionMapper);
    }

    @Test
    void finishRequiresReviewBatch() {
        AgentStepResult empty = new AgentStepResult(
                1, "paper_trade_review", AgentStepResult.Status.SUCCEEDED, "OK", null, Map.of());
        assertFalse(StructuredDecisionEngine.hasTraderReviewBatch(List.of(empty), objectMapper));

        AgentStepResult batch = new AgentStepResult(
                1, "paper_trade_review", AgentStepResult.Status.SUCCEEDED,
                "{\"decisions\":[{\"action\":\"hold\",\"symbol\":\"002463\",\"confidence\":0.2,\"reasoning\":\"观望\",\"manualReview\":false}]}",
                null, Map.of());
        assertTrue(StructuredDecisionEngine.hasTraderReviewBatch(List.of(batch), objectMapper));
    }

    @Test
    void storedDecisionsStandInWhenObservationHasNoBatch() {
        PaperAiDecision row = new PaperAiDecision();
        row.setAction("HOLD");
        row.setSymbol("002463");
        row.setName("沪电股份");
        row.setQty(0);
        row.setConfidence(new BigDecimal("0.30"));
        row.setRationale("观察");
        row.setRisksJson("[\"趋势未明\"]");
        row.setNeedsHumanReview(false);
        when(decisionMapper.selectList(any())).thenReturn(List.of(row));
        when(scenarioAgentService.run(any())).thenReturn(new AgentRunResult(
                AgentRunResult.Status.SUCCEEDED,
                "OK",
                List.of(),
                null,
                Map.of("runId", "run-1")));

        PaperAgentRuntimeAdapter.Outcome outcome = adapter.trader("上下文", "run-db", "job-1");
        assertTrue(outcome.answer().contains("002463"));
        assertTrue(outcome.traceJson().contains("\"partialSalvage\":true"));
    }
}
