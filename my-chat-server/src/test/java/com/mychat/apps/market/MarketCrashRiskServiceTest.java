package com.mychat.apps.market;

import com.mychat.apps.market.entity.vo.MarketCrashRiskVO;
import com.mychat.job.AsyncJobService;
import com.mychat.service.agent.worker.SearchWorker;
import com.mychat.vo.AsyncJobVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 同周不重搜、跨周提交任务、从搜索结果抽出三字段。 */
class MarketCrashRiskServiceTest {

    @TempDir
    Path tempDir;

    private SearchWorker searchWorker;
    private AsyncJobService asyncJobService;
    private MarketCrashRiskService service;
    private final LocalDate week37 = LocalDate.of(2026, 9, 9);
    private final LocalDate week38 = LocalDate.of(2026, 9, 14);

    @BeforeEach
    void setUp() {
        searchWorker = mock(SearchWorker.class);
        asyncJobService = mock(AsyncJobService.class);
        MarketStrategyProperties properties = new MarketStrategyProperties();
        properties.setCrashRiskFile(tempDir.resolve("market-crash-risk.json").toString());
        service = new MarketCrashRiskService(properties, searchWorker, asyncJobService);
        AsyncJobVO job = new AsyncJobVO();
        job.setId("job-1");
        when(asyncJobService.submit(anyString(), anyString(), anyString(), any(), anyBoolean()))
                .thenReturn(job);
    }

    /** runRefresh 从模型 JSON 写出 window/trigger/impact。 */
    @Test
    void runRefreshParsesThreeFields() {
        when(searchWorker.run(anyString(), nullable(String.class)))
                .thenReturn("说明\n{\"window\":\"未来1到3个月\",\"trigger\":\"利率意外上修\",\"impact\":\"风险资产回撤\"}");
        service.runRefresh("2026-W37");
        MarketCrashRiskVO vo = service.load();
        assertEquals("2026-W37", vo.getWeekKey());
        assertEquals(MarketCrashRiskService.STATUS_SUCCEEDED, vo.getStatus());
        assertEquals("未来1到3个月", vo.getWindow());
        assertEquals("利率意外上修", vo.getTrigger());
        assertEquals("风险资产回撤", vo.getImpact());
    }

    /** 本周已成功则不再搜索、不再提交任务。 */
    @Test
    void sameWeekSucceededDoesNotSearchAgain() {
        when(searchWorker.run(anyString(), nullable(String.class)))
                .thenReturn("{\"window\":\"数月内\",\"trigger\":\"信贷紧缩\",\"impact\":\"波动上升\"}");
        service.runRefresh(MarketCrashRiskService.weekKey(week37));

        MarketCrashRiskVO vo = service.ensureCurrentWeekOn(week37);
        assertEquals(MarketCrashRiskService.STATUS_SUCCEEDED, vo.getStatus());
        verify(asyncJobService, never())
                .submit(anyString(), anyString(), anyString(), any(), anyBoolean());
        verify(searchWorker).run(anyString(), nullable(String.class));
    }

    /** 跨自然周会提交新的后台任务。 */
    @Test
    void newWeekSubmitsJob() {
        when(searchWorker.run(anyString(), nullable(String.class)))
                .thenReturn("{\"window\":\"数月内\",\"trigger\":\"信贷紧缩\",\"impact\":\"波动上升\"}");
        service.runRefresh(MarketCrashRiskService.weekKey(week37));

        MarketCrashRiskVO vo = service.ensureCurrentWeekOn(week38);
        assertEquals(MarketCrashRiskService.STATUS_RUNNING, vo.getStatus());
        assertEquals(MarketCrashRiskService.weekKey(week38), vo.getWeekKey());
        assertEquals("数月内", vo.getWindow());
        verify(asyncJobService).submit(
                eq(MarketCrashRiskService.JOB_TYPE),
                anyString(),
                eq(MarketCrashRiskService.weekKey(week38)),
                isNull(),
                eq(false));
    }
}
