package com.mychat.apps.market;

import com.mychat.apps.market.entity.vo.MarketStrategyVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.client.ChatClient;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 策略 JSON 落盘与「仅保存时评价」。 */
class MarketStrategyServiceTest {

    @TempDir
    Path tempDir;

    private ChatClient chatClient;
    private MarketStrategyService service;

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        MarketStrategyProperties properties = new MarketStrategyProperties();
        properties.setStrategyFile(tempDir.resolve("market-strategy.json").toString());
        service = new MarketStrategyService(properties, chatClient);
    }

    /** 无文件时返回空字段，不调模型。 */
    @Test
    void loadDoesNotCallModelWhenFileMissing() {
        MarketStrategyVO vo = service.load();
        assertEquals("", vo.getStrategyText());
        assertEquals("", vo.getEvaluation());
        verify(chatClient, never()).prompt();
    }

    /** 空策略落盘并清空评价，不调模型。 */
    @Test
    void saveBlankClearsEvaluationWithoutCallingModel() {
        MarketStrategyVO vo = service.saveAndEvaluate("   ");
        assertEquals("", vo.getStrategyText());
        assertEquals("", vo.getEvaluation());
        verify(chatClient, never()).prompt();
    }

    /** 有正文时调一次模型并写回评价。 */
    @Test
    void saveCallsModelOnceAndPersists() {
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn("仓位过于集中。这不是投资建议。");
        MarketStrategyVO saved = service.saveAndEvaluate("只买一只股票");
        assertEquals("只买一只股票", saved.getStrategyText());
        assertTrue(saved.getEvaluation().contains("不是投资建议"));

        clearInvocations(chatClient);
        MarketStrategyVO loaded = service.load();
        assertEquals("只买一只股票", loaded.getStrategyText());
        assertEquals(saved.getEvaluation(), loaded.getEvaluation());
        verify(chatClient, never()).prompt();
    }

    /** 没有评价时拒绝优化。 */
    @Test
    void optimizeWithoutEvaluationThrows() {
        assertThrows(IllegalArgumentException.class, () -> service.optimizeAndOverwrite());
        verify(chatClient, never()).prompt();
    }

    /** 优化后覆盖策略并写回新评价。 */
    @Test
    void optimizeOverwritesStrategyAndReevaluates() {
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn("仓位过于集中。这不是投资建议。")
                .thenReturn("分散持仓，单票不超过两成，跌破买入价8%止损。")
                .thenReturn("规则更清晰。这不是投资建议。");
        service.saveAndEvaluate("只买一只股票");
        MarketStrategyVO optimized = service.optimizeAndOverwrite();
        assertTrue(optimized.getStrategyText().contains("分散持仓"));
        assertTrue(optimized.getEvaluation().contains("规则更清晰"));
        assertEquals(optimized.getStrategyText(), service.load().getStrategyText());
    }
}
