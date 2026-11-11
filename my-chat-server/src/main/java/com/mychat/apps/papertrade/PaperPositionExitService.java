package com.mychat.apps.papertrade;

import com.mychat.apps.papertrade.entity.po.PaperAiDecision;
import com.mychat.apps.papertrade.entity.po.PaperPosition;
import com.mychat.apps.papertrade.mapper.PaperAiDecisionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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

    /** 扫描持仓并写入本轮 SELL 决策（PENDING）。 */
    public void scanAndEnqueue(String runId) {
        List<PaperPosition> positions = tradeService.listPositionEntities();
        LocalDate today = LocalDate.now();
        for (PaperPosition pos : positions) {
            int avail = pos.getAvailableQty() == null ? 0 : pos.getAvailableQty();
            if (avail <= 0) {
                continue;
            }
            BigDecimal last;
            try {
                last = tradeService.lastClose(pos.getSymbol());
            } catch (Exception e) {
                log.debug("出场扫描行情失败 {}: {}", pos.getSymbol(), e.getMessage());
                continue;
            }
            if (pos.getAvgCost() == null || pos.getAvgCost().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal pnl = last.subtract(pos.getAvgCost())
                    .divide(pos.getAvgCost(), 6, RoundingMode.HALF_UP);

            String reason = null;
            if (pos.getTakeProfitPct() != null
                    && pnl.compareTo(pos.getTakeProfitPct()) >= 0) {
                reason = "TAKE_PROFIT pnl=" + pnl;
            } else if (pos.getStopLossPct() != null
                    && pnl.compareTo(pos.getStopLossPct().negate()) <= 0) {
                reason = "STOP_LOSS pnl=" + pnl;
            } else if (pos.getMaxHoldDays() != null && pos.getMaxHoldDays() > 0
                    && pos.getOpenedAt() != null) {
                long days = ChronoUnit.DAYS.between(pos.getOpenedAt().toLocalDate(), today);
                if (days >= pos.getMaxHoldDays()) {
                    reason = "MAX_HOLD_DAYS days=" + days;
                }
            }
            if (reason == null) {
                continue;
            }
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
            d.setCooldownDays(2);
            decisionMapper.insert(d);
            log.info("出场决策 {} {} qty={} {}", pos.getSymbol(), reason, avail, runId);
        }
    }
}
