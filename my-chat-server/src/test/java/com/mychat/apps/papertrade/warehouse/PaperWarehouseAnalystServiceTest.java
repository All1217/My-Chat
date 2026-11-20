package com.mychat.apps.papertrade.warehouse;

import com.mychat.apps.papertrade.PaperTradeProperties;
import com.mychat.apps.papertrade.agent.PaperAgentRuntimeAdapter;
import com.mychat.apps.papertrade.research.entity.po.PaperResearchNote;
import com.mychat.apps.papertrade.research.mapper.PaperResearchNoteMapper;
import com.mychat.apps.papertrade.trader.PaperAiContextBuilder;
import com.mychat.apps.papertrade.trader.PaperStrategyService;
import com.mychat.apps.papertrade.trader.entity.po.PaperStrategy;
import com.mychat.apps.papertrade.warehouse.entity.po.PaperWarehouseAnalystRun;
import com.mychat.apps.papertrade.warehouse.mapper.PaperWarehouseAnalystRunMapper;
import com.mychat.apps.papertrade.workflow.PaperRunLifecycle;
import com.mychat.job.AsyncJobService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 票仓分析员：单票失败不拖死整轮。 */
@ExtendWith(MockitoExtension.class)
class PaperWarehouseAnalystServiceTest {

    @Mock
    private PaperWarehouseAnalystRunMapper runMapper;
    @Mock
    private PaperResearchNoteMapper noteMapper;
    @Mock
    private PaperStrategyService strategyService;
    @Mock
    private PaperAiContextBuilder contextBuilder;
    @Mock
    private PaperAgentRuntimeAdapter agentRuntime;
    @Mock
    private AsyncJobService asyncJobService;
    @Mock
    private PaperRunLifecycle lifecycle;

    private PaperWarehouseAnalystService service;

    @BeforeEach
    void setUp() {
        PaperTradeProperties props = new PaperTradeProperties();
        props.setResearchBatchSize(15);
        service = new PaperWarehouseAnalystService(
                runMapper, noteMapper, props, strategyService, contextBuilder,
                agentRuntime, asyncJobService, lifecycle);
    }

    @Test
    void continuesAfterOneSymbolFailsAndSucceedsOverall() throws Exception {
        PaperWarehouseAnalystRun run = pendingRun("run-partial");
        when(runMapper.selectById("run-partial")).thenReturn(run);
        when(lifecycle.tryMarkRunning(eq(runMapper), any())).thenReturn(true);
        when(noteMapper.selectList(any())).thenReturn(List.of(
                note("AAA", 2), note("BBB", 2), note("CCC", 2)));
        PaperStrategy strategy = new PaperStrategy();
        strategy.setContent("策略");
        when(strategyService.requireDefault()).thenReturn(strategy);
        when(contextBuilder.build()).thenReturn(emptyContext());

        when(agentRuntime.warehouseAnalyze(anyString(), eq("run-partial"), eq("job-1")))
                .thenAnswer(inv -> {
                    String prompt = inv.getArgument(0);
                    if (prompt.contains("\"symbol\":\"BBB\"")) {
                        throw new IllegalStateException("票仓分析员失败: FAILED boom");
                    }
                    String symbol = prompt.contains("AAA") ? "AAA"
                            : prompt.contains("CCC") ? "CCC" : "X";
                    return new PaperAgentRuntimeAdapter.Outcome(
                            "OK",
                            "obs",
                            "{\"status\":\"SUCCEEDED\",\"steps\":[]}",
                            "agent-" + symbol);
                });
        when(noteMapper.selectById("AAA")).thenReturn(note("AAA", 1));
        when(noteMapper.selectById("CCC")).thenReturn(note("CCC", 2));

        service.run("run-partial");

        verify(agentRuntime, times(3)).warehouseAnalyze(anyString(), eq("run-partial"), eq("job-1"));
        ArgumentCaptor<PaperWarehouseAnalystRun> cap = ArgumentCaptor.forClass(PaperWarehouseAnalystRun.class);
        verify(runMapper, atLeast(1)).updateById(cap.capture());
        PaperWarehouseAnalystRun last = cap.getValue();
        assertEquals(PaperWarehouseAnalystRun.STATUS_SUCCEEDED, last.getStatus());
        assertEquals(1, last.getAdjustedCount());
        assertTrue(last.getStatsJson().contains("\"failedCount\":1"));
        assertTrue(last.getStatsJson().contains("BBB"));
        assertTrue(last.getFrameworkTrace().contains("AI_DEEP_GATHER"));
        assertTrue(last.getFrameworkTrace().contains("\"agents\""));
        assertTrue(last.getErrorMessage().contains("部分失败"));
    }

    @Test
    void allSymbolsFailMarksFailed() {
        PaperWarehouseAnalystRun run = pendingRun("run-all-fail");
        when(runMapper.selectById("run-all-fail")).thenReturn(run);
        when(lifecycle.tryMarkRunning(eq(runMapper), any())).thenReturn(true);
        when(noteMapper.selectList(any())).thenReturn(List.of(note("ZZZ", 2)));
        PaperStrategy strategy = new PaperStrategy();
        strategy.setContent("策略");
        when(strategyService.requireDefault()).thenReturn(strategy);
        when(contextBuilder.build()).thenReturn(emptyContext());
        when(agentRuntime.warehouseAnalyze(anyString(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("票仓分析员失败: BUDGET_EXHAUSTED"));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.run("run-all-fail"));
        assertTrue(ex.getMessage().contains("全部失败"));
        ArgumentCaptor<PaperWarehouseAnalystRun> cap = ArgumentCaptor.forClass(PaperWarehouseAnalystRun.class);
        verify(runMapper, atLeast(1)).updateById(cap.capture());
        assertEquals(PaperWarehouseAnalystRun.STATUS_FAILED, cap.getValue().getStatus());
    }

    private static PaperWarehouseAnalystRun pendingRun(String id) {
        PaperWarehouseAnalystRun run = new PaperWarehouseAnalystRun();
        run.setId(id);
        run.setJobId("job-1");
        run.setStatus(PaperWarehouseAnalystRun.STATUS_PENDING);
        run.setScreenedCount(0);
        run.setAdjustedCount(0);
        return run;
    }

    private static PaperResearchNote note(String symbol, int tier) {
        PaperResearchNote n = new PaperResearchNote();
        n.setSymbol(symbol);
        n.setName(symbol);
        n.setValueTier(tier);
        n.setStory("s");
        n.setRisks("r");
        n.setFitScore(BigDecimal.valueOf(0.5));
        n.setEvidenceScore(BigDecimal.valueOf(0.5));
        return n;
    }

    private static PaperAiContextBuilder.BuiltContext emptyContext() {
        return new PaperAiContextBuilder.BuiltContext(
                "{}",
                List.of(),
                Set.of(),
                Map.of(),
                Map.of(),
                null,
                BigDecimal.ZERO,
                true,
                BigDecimal.valueOf(50000),
                Set.of());
    }
}
