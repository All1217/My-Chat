package com.mychat.apps.market;

import com.mychat.apps.market.entity.vo.MarketIndexPeItemVO;
import com.mychat.apps.market.entity.vo.MarketIndexPeVO;
import com.mychat.service.agent.worker.SearchWorker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 同日不搜索、跨日解析 JSON、失败保留昨日。 */
class MarketIndexPeServiceTest {

    @TempDir
    Path tempDir;

    private SearchWorker searchWorker;
    private MarketIndexPeService service;
    private final LocalDate day = LocalDate.of(2026, 9, 10);

    @BeforeEach
    void setUp() {
        searchWorker = mock(SearchWorker.class);
        MarketStrategyProperties properties = new MarketStrategyProperties();
        properties.setIndexPeFile(tempDir.resolve("market-index-pe.json").toString());
        service = new MarketIndexPeService(properties, searchWorker);
    }

    /** 同日 SUCCEEDED 不调 SearchWorker。 */
    @Test
    void sameDaySucceededDoesNotSearch() throws Exception {
        Files.writeString(
                tempDir.resolve("market-index-pe.json"),
                """
                        {
                          "cacheDate": "2026-09-10",
                          "status": "SUCCEEDED",
                          "generatedAt": "2026-09-10T00:00:00Z",
                          "errorMessage": "",
                          "indices": [
                            {"code":"SPX","name":"标普500","pe":28.0,"percentile":80.0,"sampleSize":0,"comment":"分位偏高"}
                          ]
                        }
                        """,
                StandardCharsets.UTF_8);

        MarketIndexPeVO vo = service.ensureTodayOn(day);

        assertEquals(MarketIndexPeService.STATUS_SUCCEEDED, vo.getStatus());
        assertEquals(1, vo.getIndices().size());
        assertEquals("标普500", vo.getIndices().get(0).getName());
        verify(searchWorker, never()).run(anyString(), nullable(String.class));
    }

    /** 同日 SUCCEEDED 但 PE/分位全空（旧东财空缓存）仍会搜索。 */
    @Test
    void sameDayEmptySucceededStillSearches() throws Exception {
        Files.writeString(
                tempDir.resolve("market-index-pe.json"),
                """
                        {
                          "cacheDate": "2026-09-10",
                          "status": "SUCCEEDED",
                          "generatedAt": "2026-09-10T14:05:25Z",
                          "errorMessage": "",
                          "indices": [
                            {"code":"SPX","name":"标普500","pe":null,"percentile":null,"comment":"历史样本不足"},
                            {"code":"DJIA","name":"道琼斯","pe":null,"percentile":null,"comment":"历史样本不足"},
                            {"code":"NDX","name":"纳斯达克100","pe":null,"percentile":null,"comment":"历史样本不足"}
                          ]
                        }
                        """,
                StandardCharsets.UTF_8);
        when(searchWorker.run(anyString(), nullable(String.class)))
                .thenReturn("""
                        [{"code":"SPX","name":"标普500","pe":28.1,"percentile":82,"comment":"估值偏高"},
                         {"code":"DJIA","name":"道琼斯","pe":22.0,"percentile":60,"comment":"大致中性"},
                         {"code":"NDX","name":"纳斯达克100","pe":35.0,"percentile":90,"comment":"分位靠上"}]
                        """);

        MarketIndexPeVO vo = service.ensureTodayOn(day);

        assertEquals(MarketIndexPeService.STATUS_SUCCEEDED, vo.getStatus());
        assertEquals(28.1, vo.getIndices().get(0).getPe());
        verify(searchWorker).run(anyString(), nullable(String.class));
    }

    /** 跨自然日会搜索，并能从夹杂说明的 JSON 数组写出三条。 */
    @Test
    void newDayParsesJsonArrayFromProse() {
        when(searchWorker.run(anyString(), nullable(String.class)))
                .thenReturn("""
                        以下为整理结果，仅供参考。
                        [
                          {"code":"SPX","name":"标普500","pe":28.1,"percentile":82,"comment":"估值偏高"},
                          {"code":"DJIA","name":"道琼斯","pe":22.0,"percentile":60,"comment":"大致中性"},
                          {"code":"NDX","name":"纳斯达克100","pe":35.0,"percentile":90,"comment":"分位靠上"}
                        ]
                        不是投资建议。
                        """);

        MarketIndexPeVO vo = service.ensureTodayOn(day);

        assertEquals(MarketIndexPeService.STATUS_SUCCEEDED, vo.getStatus());
        assertEquals(3, vo.getIndices().size());
        MarketIndexPeItemVO spx = vo.getIndices().get(0);
        assertEquals("SPX", spx.getCode());
        assertEquals(28.1, spx.getPe());
        assertEquals(82.0, spx.getPercentile());
        assertEquals("估值偏高", spx.getComment());
        assertEquals(22.0, vo.getIndices().get(1).getPe());
        assertEquals(35.0, vo.getIndices().get(2).getPe());
        verify(searchWorker).run(anyString(), nullable(String.class));
    }

    /** 解析失败则 status=FAILED，昨日三条仍在。 */
    @Test
    void parseFailureKeepsYesterdayRows() throws Exception {
        Files.writeString(
                tempDir.resolve("market-index-pe.json"),
                """
                        {
                          "cacheDate": "2026-09-09",
                          "status": "SUCCEEDED",
                          "generatedAt": "2026-09-09T00:00:00Z",
                          "errorMessage": "",
                          "indices": [
                            {"code":"SPX","name":"标普500","pe":27.0,"percentile":70.0,"comment":"昨日标普"},
                            {"code":"DJIA","name":"道琼斯","pe":21.0,"percentile":55.0,"comment":"昨日道指"},
                            {"code":"NDX","name":"纳斯达克100","pe":34.0,"percentile":88.0,"comment":"昨日纳指"}
                          ]
                        }
                        """,
                StandardCharsets.UTF_8);
        when(searchWorker.run(anyString(), nullable(String.class)))
                .thenReturn("这次检索没有可用数字");

        MarketIndexPeVO vo = service.ensureTodayOn(day);

        assertEquals(MarketIndexPeService.STATUS_FAILED, vo.getStatus());
        assertEquals("2026-09-10", vo.getCacheDate());
        assertEquals(3, vo.getIndices().size());
        assertEquals(27.0, vo.getIndices().get(0).getPe());
        assertEquals("昨日标普", vo.getIndices().get(0).getComment());
        assertEquals(21.0, vo.getIndices().get(1).getPe());
        assertEquals(34.0, vo.getIndices().get(2).getPe());
        verify(searchWorker).run(anyString(), nullable(String.class));
    }
}
