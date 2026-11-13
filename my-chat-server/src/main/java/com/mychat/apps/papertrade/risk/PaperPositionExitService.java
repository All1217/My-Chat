package com.mychat.apps.papertrade.risk;

import com.mychat.apps.market.EtfPremiumClient;
import com.mychat.apps.market.entity.vo.MarketQuoteVO;
import com.mychat.apps.papertrade.ledger.PaperTradeCalendar;
import com.mychat.apps.papertrade.ledger.PaperTradeService;
import com.mychat.apps.papertrade.trader.entity.po.PaperAiDecision;
import com.mychat.apps.papertrade.ledger.entity.po.PaperPosition;
import com.mychat.apps.papertrade.trader.mapper.PaperAiDecisionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 持仓扫描：触及止盈/止损/最长持有天数时写入 SELL 决策（与 AI 轮共用执行路径）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaperPositionExitService {

    private final PaperTradeService tradeService;
    private final PaperAiDecisionMapper decisionMapper;
    private final PaperSymbolGate symbolGate;

    /** 扫描持仓并写入本轮 SELL 决策（PENDING）。 */
    public void scanAndEnqueue(String runId) {
        List<PaperPosition> positions = tradeService.listPositionEntities();
        LocalDate today = LocalDate.now();
        for (PaperPosition pos : positions) {
            int avail = pos.getAvailableQty() == null ? 0 : pos.getAvailableQty();
            if (avail <= 0) {
                continue;
            }
            MarketQuoteVO quote;
            BigDecimal last;
            try {
                quote = tradeService.quoteMonth(pos.getSymbol());
                last = closeOf(quote);
            } catch (Exception e) {
                log.debug("出场扫描行情失败 {}: {}", pos.getSymbol(), e.getMessage());
                continue;
            }
            if (symbolGate.analyze(quote).limitDown()) {
                log.info("跌停跳过出场 {}", pos.getSymbol());
                continue;
            }
            if (pos.getAvgCost() == null || pos.getAvgCost().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal pnl = last.subtract(pos.getAvgCost())
                    .divide(pos.getAvgCost(), 6, RoundingMode.HALF_UP);
            int holdDays = pos.getOpenedAt() == null
                    ? 0 : PaperTradeCalendar.weekdaysSince(pos.getOpenedAt().toLocalDate(), today);
            String code = pickExit(pnl, pos.getTakeProfitPct(), pos.getStopLossPct(),
                    holdDays, pos.getMaxHoldDays());
            if (code == null) {
                continue;
            }
            String reason = code + " pnl=" + pnl + " holdDays=" + holdDays;
            PaperAiDecision d = new PaperAiDecision();
            d.setId(UUID.randomUUID().toString().replace("-", ""));
            d.setRunId(runId);
            d.setAction(PaperAiDecision.ACTION_SELL);
            d.setSymbol(pos.getSymbol());
            d.setQty(avail);
            d.setRefPrice(last);
            d.setConfidence(BigDecimal.ONE);
            d.setNeedsHumanReview(false);
            d.setRationale("持仓扫描自动出场：" + reason);
            d.setRisksJson("[\"exit_scan\"]");
            d.setExecStatus(PaperAiDecision.EXEC_PENDING);
            d.setCooldownDays(EtfPremiumClient.looksLikeEtf(pos.getSymbol(), null) ? 1 : 2);
            decisionMapper.insert(d);
            log.info("出场决策 {} {} qty={} {}", pos.getSymbol(), reason, avail, runId);
        }
    }

    /** 止损优先于止盈，再看最长持有工作日。未触发返回 null。 */
    public static String pickExit(BigDecimal pnl, BigDecimal takeProfit, BigDecimal stopLoss,
                           int holdDays, Integer maxHoldDays) {
        if (pnl != null && stopLoss != null && pnl.compareTo(stopLoss.negate()) <= 0) {
            return "STOP_LOSS";
        }
        if (pnl != null && takeProfit != null && pnl.compareTo(takeProfit) >= 0) {
            return "TAKE_PROFIT";
        }
        if (maxHoldDays != null && maxHoldDays > 0 && holdDays >= maxHoldDays) {
            return "MAX_HOLD";
        }
        return null;
    }

    private static BigDecimal closeOf(MarketQuoteVO quote) {
        if (quote == null) {
            throw new IllegalArgumentException("无行情");
        }
        if (quote.getLastPrice() != null && quote.getLastPrice() > 0) {
            return BigDecimal.valueOf(quote.getLastPrice()).setScale(4, RoundingMode.HALF_UP);
        }
        if (quote.getHistory() != null && !quote.getHistory().isEmpty()) {
            Double close = quote.getHistory().get(quote.getHistory().size() - 1).getClose();
            if (close != null && close > 0) {
                return BigDecimal.valueOf(close).setScale(4, RoundingMode.HALF_UP);
            }
        }
        throw new IllegalArgumentException("无法取得最新收盘价");
    }
}
