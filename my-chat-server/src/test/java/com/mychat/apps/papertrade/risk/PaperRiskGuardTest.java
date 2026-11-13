package com.mychat.apps.papertrade.risk;

import com.mychat.apps.papertrade.PaperTradeProperties;
import com.mychat.apps.papertrade.trader.entity.po.PaperAiDecision;
import com.mychat.apps.papertrade.ledger.entity.po.PaperPosition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 买入裁股、禁止加仓、暂停和跌停拒卖。 */
class PaperRiskGuardTest {

    private PaperRiskGuard guard;

    @BeforeEach
    void setUp() {
        guard = new PaperRiskGuard(new PaperTradeProperties());
    }

    @Test
    void capsQtyByRiskBudget() {
        PaperAiDecision d = buy("600519", new BigDecimal("10"), new BigDecimal("0.03"), new BigDecimal("0.04"), 5000);
        PaperGateContext ctx = book(new BigDecimal("20000"), new BigDecimal("20000"), 0.02);
        ctx.setFacts(Map.of("600519", stock("600519")));

        assertNull(guard.assessBuy(d, ctx));
        assertEquals(1300, d.getQty());
    }

    @Test
    void skipsWhenOneLotBreaksRiskBudget() {
        PaperAiDecision d = buy("600519", new BigDecimal("300"), new BigDecimal("0.03"), new BigDecimal("0.04"), 100);
        PaperGateContext ctx = book(new BigDecimal("20000"), new BigDecimal("20000"), 0.02);
        ctx.setFacts(Map.of("600519", stock("600519")));

        assertEquals("LOT_BELOW_MIN", guard.assessBuy(d, ctx));
    }

    @Test
    void rejectsAddOnAndPause() {
        PaperAiDecision d = buy("600519", new BigDecimal("10"), new BigDecimal("0.03"), new BigDecimal("0.04"), 100);
        PaperGateContext ctx = book(new BigDecimal("20000"), new BigDecimal("20000"), 0.02);
        PaperPosition held = new PaperPosition();
        held.setSymbol("600519");
        ctx.setPositions(List.of(held));
        assertEquals("ADD_ON_FORBIDDEN", guard.assessBuy(d, ctx));

        ctx.setPositions(List.of());
        ctx.setBuyPaused(true);
        assertEquals("BUY_PAUSED", guard.assessBuy(d, ctx));
    }

    @Test
    void rejectsLimitDownSell() {
        PaperAiDecision d = new PaperAiDecision();
        d.setAction(PaperAiDecision.ACTION_SELL);
        d.setSymbol("600519");
        d.setQty(100);
        PaperSymbolFacts facts = new PaperSymbolFacts(
                "600519", "贵州茅台", false, false, false, false, false, true, true, 80, 80_000_000d);
        assertEquals("LIMIT_DOWN", guard.rejectSellReason(d, List.of(), facts));
    }

    private static PaperAiDecision buy(String symbol, BigDecimal price, BigDecimal stop, BigDecimal tp, int qty) {
        PaperAiDecision d = new PaperAiDecision();
        d.setAction(PaperAiDecision.ACTION_BUY);
        d.setSymbol(symbol);
        d.setName("贵州茅台");
        d.setRefPrice(price);
        d.setStopLossPct(stop);
        d.setTakeProfitPct(tp);
        d.setQty(qty);
        return d;
    }

    private static PaperGateContext book(BigDecimal cash, BigDecimal equity, double budget) {
        PaperGateContext ctx = new PaperGateContext();
        ctx.setPositions(List.of());
        ctx.setCooldownSymbols(java.util.Set.of());
        ctx.setCash(cash);
        ctx.setEquity(equity);
        ctx.setRiskBudgetPct(budget);
        return ctx;
    }

    private static PaperSymbolFacts stock(String symbol) {
        return new PaperSymbolFacts(symbol, "贵州茅台", false, false, false, false, false, false, true, 80, 80_000_000d);
    }
}
