package com.mychat.apps.market;

import com.mychat.apps.market.entity.vo.KlinePointVO;
import com.mychat.apps.market.entity.vo.MarketQuoteVO;
import com.mychat.apps.market.entity.vo.MarketStrategyVO;
import com.mychat.apps.market.entity.vo.MarketWatchlistAlertVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.client.ChatClient;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 自选缓存、空 pick 与「无策略不调模型」。 */
class MarketWatchlistServiceTest {

    @TempDir
    Path tempDir;

    private ChatClient chatClient;
    private MarketQuoteClient quoteClient;
    private MarketStrategyService strategyService;
    private MarketWatchlistService service;
    private final LocalDate day = LocalDate.of(2026, 9, 9);

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        quoteClient = mock(MarketQuoteClient.class);
        strategyService = mock(MarketStrategyService.class);
        MarketStrategyProperties properties = new MarketStrategyProperties();
        properties.setWatchlistFile(tempDir.resolve("market-watchlist.json").toString());
        service = new MarketWatchlistService(properties, strategyService, quoteClient, chatClient);
    }

    /** 无自选时不调模型。 */
    @Test
    void alertWithoutWatchlistDoesNotCallModel() {
        MarketWatchlistAlertVO vo = service.alertOn(day);
        assertEquals("请先添加自选股", vo.getReason());
        verify(chatClient, never()).prompt();
        verify(quoteClient, never()).fetch(anyString(), anyString());
    }

    /** 无策略时不调模型。 */
    @Test
    void alertWithoutStrategyDoesNotCallModel() {
        service.save(List.of("600519"));
        when(strategyService.load()).thenReturn(new MarketStrategyVO());
        MarketWatchlistAlertVO vo = service.alertOn(day);
        assertEquals("请先保存投资策略", vo.getReason());
        verify(chatClient, never()).prompt();
    }

    /** 模型返回空 pick 合法，同日同指纹不再调模型。 */
    @Test
    void emptyPickIsCachedForTheSameDay() {
        service.save(List.of("600519"));
        MarketStrategyVO strategy = new MarketStrategyVO();
        strategy.setStrategyText("分批加仓");
        when(strategyService.load()).thenReturn(strategy);
        when(quoteClient.fetch(eq("600519"), eq("1Y"))).thenReturn(quote("600519", "茅台"));
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn("{\"pickSymbol\":\"\",\"reason\":\"暂无加仓提醒\"}");

        MarketWatchlistAlertVO first = service.alertOn(day);
        assertEquals("", first.getPickSymbol());
        assertEquals("暂无加仓提醒", first.getReason());
        assertFalse(first.isCached());

        clearInvocations(chatClient, quoteClient);
        MarketWatchlistAlertVO second = service.alertOn(day);
        assertTrue(second.isCached());
        assertEquals("暂无加仓提醒", second.getReason());
        verify(chatClient, never()).prompt();
        verify(quoteClient, never()).fetch(anyString(), anyString());
    }

    /** 改自选后缓存失效，会再调模型。 */
    @Test
    void changingWatchlistBustsCache() {
        service.save(List.of("600519"));
        MarketStrategyVO strategy = new MarketStrategyVO();
        strategy.setStrategyText("分批加仓");
        when(strategyService.load()).thenReturn(strategy);
        when(quoteClient.fetch(anyString(), eq("1Y"))).thenReturn(quote("600519", "茅台"));
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn("{\"pickSymbol\":\"600519\",\"reason\":\"回撤 10%，RSI14=28\"}");
        service.alertOn(day);

        when(quoteClient.fetch(eq("000001"), eq("1Y"))).thenReturn(quote("000001", "平安"));
        service.save(List.of("000001"));
        clearInvocations(chatClient);
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn("{\"pickSymbol\":\"\",\"reason\":\"暂无加仓提醒\"}");
        MarketWatchlistAlertVO vo = service.alertOn(day);
        assertFalse(vo.isCached());
        assertEquals("", vo.getPickSymbol());
    }

    private static MarketQuoteVO quote(String symbol, String name) {
        MarketQuoteVO quote = new MarketQuoteVO();
        quote.setSymbol(symbol);
        quote.setName(name);
        List<KlinePointVO> history = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            KlinePointVO bar = new KlinePointVO();
            bar.setDate("2026-01-" + String.format("%02d", i + 1));
            bar.setOpen(100.0);
            bar.setClose(100.0 - i);
            bar.setHigh(101.0);
            bar.setLow(99.0);
            bar.setVolume(1000L);
            history.add(bar);
        }
        quote.setHistory(history);
        return quote;
    }
}
