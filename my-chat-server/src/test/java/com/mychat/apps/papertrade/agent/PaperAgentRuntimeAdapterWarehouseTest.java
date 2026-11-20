package com.mychat.apps.papertrade.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mychat.agent.api.AgentRunResult;
import com.mychat.agent.api.AgentStepResult;
import com.mychat.agent.runtime.ScenarioAgentService;
import com.mychat.agent.runtime.StructuredOutputRepairService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** 票仓分析员软成功：预算耗尽但 updateValueTier 已落库。 */
@ExtendWith(MockitoExtension.class)
class PaperAgentRuntimeAdapterWarehouseTest {

    @Mock
    private ScenarioAgentService scenarioAgentService;
    @Mock
    private StructuredOutputRepairService repairService;

    private PaperAgentRuntimeAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new PaperAgentRuntimeAdapter(
                scenarioAgentService, repairService, new ObjectMapper());
    }

    @Test
    void looksLikeTierUpdateOkDetectsToolReceipt() {
        assertTrue(PaperAgentRuntimeAdapter.looksLikeTierUpdateOk(
                "{\"ok\":true,\"to\":2,\"from\":2,\"symbol\":\"300308\"}"));
        assertTrue(PaperAgentRuntimeAdapter.looksLikeTierUpdateOk(
                "工具返回 {\"ok\": true, \"symbol\": \"688825\", \"from\": 2, \"to\": 2}"));
    }

    @Test
    void budgetExhaustedWithTierUpdateIsSoftSuccess() {
        AgentStepResult step = new AgentStepResult(
                1,
                "paper_warehouse_analyze",
                AgentStepResult.Status.SUCCEEDED,
                "深搜完成。{\"ok\":true,\"symbol\":\"688825\",\"from\":2,\"to\":2}",
                null,
                Map.of("toolCalls", 1));
        AgentRunResult result = new AgentRunResult(
                AgentRunResult.Status.BUDGET_EXHAUSTED,
                "",
                List.of(step, step, step),
                "超过最大步骤预算",
                Map.of("runId", "run-soft"));
        when(scenarioAgentService.run(any())).thenReturn(result);

        PaperAgentRuntimeAdapter.Outcome outcome = assertDoesNotThrow(
                () -> adapter.warehouseAnalyze("分析 688825", "biz", "job-1"));
        assertTrue(outcome.traceJson().contains("\"partialSalvage\":true"));
        assertTrue(outcome.answer().contains("OK"));
    }

    @Test
    void budgetExhaustedWithoutTierUpdateStillThrows() {
        AgentStepResult step = new AgentStepResult(
                1,
                "paper_warehouse_analyze",
                AgentStepResult.Status.SUCCEEDED,
                "仅行情，未调等级",
                null,
                Map.of("toolCalls", 1));
        when(scenarioAgentService.run(any())).thenReturn(new AgentRunResult(
                AgentRunResult.Status.BUDGET_EXHAUSTED,
                "",
                List.of(step),
                "超过最大步骤预算",
                Map.of("runId", "run-hard")));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> adapter.warehouseAnalyze("分析失败票", "biz", "job-1"));
        assertTrue(ex.getMessage().contains("BUDGET_EXHAUSTED"));
    }
}
