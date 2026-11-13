package com.mychat.apps.papertrade.risk;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mychat.apps.papertrade.ledger.PaperTradeCalendar;
import com.mychat.apps.papertrade.ledger.PaperTradeService;
import com.mychat.apps.papertrade.ledger.entity.po.PaperAccount;
import com.mychat.apps.papertrade.ledger.entity.po.PaperOrder;
import com.mychat.apps.papertrade.risk.entity.po.PaperStageSummary;
import com.mychat.apps.papertrade.ledger.entity.vo.PaperAccountVO;
import com.mychat.apps.papertrade.ledger.mapper.PaperOrderMapper;
import com.mychat.apps.papertrade.risk.mapper.PaperStageSummaryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 现金枯竭时写阶段总结，再把账户重置到初始资金。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaperStageService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PaperTradeService tradeService;
    private final PaperOrderMapper orderMapper;
    private final PaperStageSummaryMapper summaryMapper;

    /** 最近一条总结正文；没有则返回 null。 */
    public String latestText() {
        PaperStageSummary latest = summaryMapper.selectOne(new LambdaQueryWrapper<PaperStageSummary>()
                .orderByDesc(PaperStageSummary::getCreatedAt)
                .last("LIMIT 1"));
        return latest == null ? null : latest.getSummaryText();
    }

    /** 把总结贴到账户视图上。 */
    public PaperAccountVO attachLatest(PaperAccountVO vo) {
        if (vo != null) {
            vo.setLatestSummary(latestText());
        }
        return vo;
    }

    /**
     * 用当前订单写出总结，然后重置账本。重置不删除历史总结。
     */
    @Transactional
    public void summarizeAndReset(BigDecimal endEquity) {
        PaperAccount account = tradeService.requireAccount();
        // 1. 取出本阶段订单。重置会删订单，所以必须先算完。
        List<PaperOrder> orders = orderMapper.selectList(new LambdaQueryWrapper<PaperOrder>()
                .eq(PaperOrder::getAccountId, account.getId())
                .orderByAsc(PaperOrder::getCreatedAt));
        PaperStageSummary row = build(account, orders, endEquity == null ? account.getCash() : endEquity);
        summaryMapper.insert(row);
        log.info("阶段总结 #{} trades={} equity={}", row.getStageNo(), row.getTradeCount(), row.getEndEquity());
        // 2. 清空持仓、订单、冷却，现金回到初始资金。
        tradeService.resetAccount();
    }

    private PaperStageSummary build(PaperAccount account, List<PaperOrder> orders, BigDecimal endEquity) {
        List<PaperOrder> sells = new ArrayList<>();
        BigDecimal totalFee = BigDecimal.ZERO;
        Map<String, Integer> sources = new LinkedHashMap<>();
        Map<String, LocalDateTime> opened = new LinkedHashMap<>();
        List<Integer> holdDays = new ArrayList<>();
        BigDecimal winSum = BigDecimal.ZERO;
        BigDecimal lossSum = BigDecimal.ZERO;
        int wins = 0;
        int losses = 0;
        // 1. 按时间把买单和随后的卖单配成一笔，并累计费用与出场原因
        for (PaperOrder order : orders) {
            if (order.getFee() != null) {
                totalFee = totalFee.add(order.getFee());
            }
            if (!PaperOrder.STATUS_FILLED.equals(order.getStatus())) {
                continue;
            }
            if (PaperOrder.SIDE_BUY.equals(order.getSide())) {
                opened.putIfAbsent(order.getSymbol(), order.getCreatedAt());
                continue;
            }
            sells.add(order);
            String reason = order.getExitReason() == null ? "UNKNOWN" : order.getExitReason();
            sources.merge(reason, 1, Integer::sum);
            BigDecimal pnl = order.getRealizedPnl();
            if (pnl != null && pnl.compareTo(BigDecimal.ZERO) > 0) {
                wins++;
                winSum = winSum.add(pnl);
            } else if (pnl != null && pnl.compareTo(BigDecimal.ZERO) < 0) {
                losses++;
                lossSum = lossSum.add(pnl.abs());
            }
            LocalDateTime openAt = opened.remove(order.getSymbol());
            if (openAt != null && order.getCreatedAt() != null) {
                holdDays.add(PaperTradeCalendar.weekdaysSince(openAt.toLocalDate(), order.getCreatedAt().toLocalDate()));
            }
        }
        int stageNo = nextStageNo();
        // 2. 胜率、盈亏比、平均持有工作日
        BigDecimal winRate = sells.isEmpty() ? null
                : BigDecimal.valueOf(wins).divide(BigDecimal.valueOf(sells.size()), 6, RoundingMode.HALF_UP);
        BigDecimal payoff = losses == 0 || wins == 0 ? null
                : winSum.divide(BigDecimal.valueOf(wins), 6, RoundingMode.HALF_UP)
                .divide(lossSum.divide(BigDecimal.valueOf(losses), 6, RoundingMode.HALF_UP), 6, RoundingMode.HALF_UP);
        BigDecimal avgHold = holdDays.isEmpty() ? null : BigDecimal.valueOf(
                holdDays.stream().mapToInt(Integer::intValue).average().orElse(0))
                .setScale(2, RoundingMode.HALF_UP);
        LocalDateTime now = LocalDateTime.now();
        PaperStageSummary row = new PaperStageSummary();
        row.setId(UUID.randomUUID().toString().replace("-", ""));
        row.setStageNo(stageNo);
        row.setStartedAt(account.getStageStartedAt());
        row.setEndedAt(now);
        row.setStartCash(account.getInitialCash() == null ? BigDecimal.ZERO : account.getInitialCash());
        row.setEndEquity(endEquity.setScale(4, RoundingMode.HALF_UP));
        row.setTradeCount(sells.size());
        row.setWinRate(winRate);
        row.setPayoffRatio(payoff);
        row.setMaxDrawdown(account.getMaxDrawdown() == null ? BigDecimal.ZERO : account.getMaxDrawdown());
        row.setAvgHoldDays(avgHold);
        row.setTotalFee(totalFee.setScale(4, RoundingMode.HALF_UP));
        row.setLossSources(sources.toString());
        row.setSummaryText(render(row, sources));
        return row;
    }

    private int nextStageNo() {
        PaperStageSummary latest = summaryMapper.selectOne(new LambdaQueryWrapper<PaperStageSummary>()
                .orderByDesc(PaperStageSummary::getStageNo)
                .last("LIMIT 1"));
        return latest == null || latest.getStageNo() == null ? 1 : latest.getStageNo() + 1;
    }

    private static String render(PaperStageSummary row, Map<String, Integer> sources) {
        String started = row.getStartedAt() == null ? "未知" : row.getStartedAt().format(TS);
        String ended = row.getEndedAt().format(TS);
        String win = row.getWinRate() == null ? "无平仓" : row.getWinRate().toPlainString();
        String payoff = row.getPayoffRatio() == null ? "无法计算" : row.getPayoffRatio().toPlainString();
        String hold = row.getAvgHoldDays() == null ? "无" : row.getAvgHoldDays().toPlainString();
        return """
                阶段%d %s 至 %s。起始资金 %s，结束权益 %s。平仓 %d 笔，胜率 %s，盈亏比 %s，最大回撤 %s，平均持有工作日 %s，费用合计 %s（滑点按 0 计，出场价是最新收盘价，不是跳空后的止损价）。出场原因 %s。下一阶段从初始资金重新开始，不继承持仓。
                """.formatted(
                row.getStageNo(),
                started,
                ended,
                row.getStartCash().toPlainString(),
                row.getEndEquity().toPlainString(),
                row.getTradeCount(),
                win,
                payoff,
                row.getMaxDrawdown().toPlainString(),
                hold,
                row.getTotalFee().toPlainString(),
                sources.isEmpty() ? "无" : sources.toString()
        ).trim();
    }
}
