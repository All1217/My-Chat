package com.mychat.apps.papertrade.evaluator;

import com.mychat.apps.papertrade.evaluator.entity.po.PaperTradeEvaluatorRun;
import com.mychat.apps.papertrade.evaluator.mapper.PaperTradeEvaluatorRunMapper;
import com.mychat.apps.papertrade.workflow.PaperRunLifecycle;
import com.mychat.job.AsyncJobService;
import com.mychat.vo.AsyncJobVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 枯竭唤醒 / 周更提交冒烟。 */
@ExtendWith(MockitoExtension.class)
class PaperTradeEvaluatorLiquidityTest {

    @Mock
    private PaperTradeEvaluatorRunMapper runMapper;
    @Mock
    private AsyncJobService asyncJobService;
    @Mock
    private PaperRunLifecycle lifecycle;

    @InjectMocks
    private PaperTradeEvaluatorService service;

    @Test
    void liquidityResetSkipsWhenActive() {
        when(runMapper.selectCount(any())).thenReturn(1L);
        var vo = service.submitRun(PaperTradeEvaluatorRun.TRIGGER_LIQUIDITY_RESET);
        assertEquals("SKIPPED_ACTIVE", vo.getStatus());
        verify(runMapper, never()).insert(any(PaperTradeEvaluatorRun.class));
    }

    @Test
    void weeklySubmitInsertsRun() {
        when(runMapper.selectCount(any())).thenReturn(0L);
        AsyncJobVO job = new AsyncJobVO();
        job.setId("job-1");
        when(asyncJobService.submit(eq(PaperTradeEvaluatorService.JOB_TYPE), anyString(),
                anyString(), isNull(), anyBoolean())).thenReturn(job);

        var vo = service.submitWeekly();
        ArgumentCaptor<PaperTradeEvaluatorRun> cap = ArgumentCaptor.forClass(PaperTradeEvaluatorRun.class);
        verify(runMapper).insert(cap.capture());
        assertEquals(PaperTradeEvaluatorRun.TRIGGER_WEEKLY, cap.getValue().getTriggerType());
        assertEquals(PaperTradeEvaluatorRun.STATUS_PENDING, vo.getStatus());
        assertEquals("job-1", vo.getJobId());
    }
}
