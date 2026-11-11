package com.mychat.apps.papertrade;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mychat.apps.market.EtfPremiumClient;
import com.mychat.apps.market.MarketDipIndicators;
import com.mychat.apps.market.MarketQuoteClient;
import com.mychat.apps.market.MarketWatchlistService;
import com.mychat.apps.market.entity.vo.MarketQuoteVO;
import com.mychat.apps.market.entity.vo.MarketWatchlistVO;
import com.mychat.apps.papertrade.entity.po.PaperAccount;
import com.mychat.apps.papertrade.entity.po.PaperAiDecision;
import com.mychat.apps.papertrade.entity.po.PaperCooldown;
import com.mychat.apps.papertrade.entity.po.PaperPosition;
import com.mychat.apps.papertrade.mapper.PaperAiDecisionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 组装 AI 一轮上下文：账户/持仓/冷却/候选指标/溢价；行情失败则剔除候选。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaperAiContextBuilder {

    private static final String RANGE = "1Y";
    private static final int MIN_BARS = 30;

    private final PaperTradeService tradeService;
    private final PaperTradeProperties properties;
    private final MarketWatchlistService watchlistService;
    private final MarketQuoteClient quoteClient;
    private final EtfPremiumClient premiumClient;
    private final PaperAiDecisionMapper decisionMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 构建上下文；同一轮复用溢价 session。 */
    public BuiltContext build() {
        tradeService.releaseT1();
        PaperAccount account = tradeService.requireAccount();
        List<PaperPosition> positions = tradeService.listPositionEntities();
        List<PaperCooldown> cooldowns = tradeService.listActiveCooldowns();
        Set<String> cooldownSymbols = new LinkedHashSet<>();
        for (PaperCooldown c : cooldowns) {
            cooldownSymbols.add(c.getSymbol());
        }

        Set<String> candidates = new LinkedHashSet<>();
        for (PaperPosition p : positions) {
            candidates.add(p.getSymbol());
        }
        MarketWatchlistVO wl = watchlistService.load();
        if (wl.getSymbols() != null) {
            candidates.addAll(wl.getSymbols());
        }
        if (candidates.isEmpty() && StringUtils.hasText(properties.getFallbackSymbols())) {
            for (String s : properties.getFallbackSymbols().split(",")) {
                if (StringUtils.hasText(s)) {
                    candidates.add(s.trim().toUpperCase(Locale.ROOT));
                }
            }
        }

        EtfPremiumClient.Session premiumSession = premiumClient.newSession();
        Map<String, EtfPremiumClient.PremiumQuote> premiums = new LinkedHashMap<>();
        ArrayNode candidateArr = objectMapper.createArrayNode();
        List<String> dropped = new ArrayList<>();

        for (String symbol : candidates) {
            if (cooldownSymbols.contains(symbol) && positions.stream()
                    .noneMatch(p -> symbol.equalsIgnoreCase(p.getSymbol()))) {
                // 冷却中且非持仓：不进买入候选，但仍可跳过
                dropped.add(symbol + "(cooldown)");
                continue;
            }
            try {
                MarketQuoteVO quote = quoteClient.fetch(symbol, RANGE);
                if (quote.getHistory() == null || quote.getHistory().size() < MIN_BARS) {
                    dropped.add(symbol + "(few_bars)");
                    continue;
                }
                MarketDipIndicators.Snapshot snap = MarketDipIndicators.from(quote);
                ObjectNode row = objectMapper.createObjectNode();
                row.put("symbol", snap.symbol());
                row.put("name", snap.name());
                putNum(row, "lastPrice", snap.lastPrice());
                putNum(row, "changePct", snap.changePct());
                putNum(row, "vsMa20Pct", snap.vsMa20Pct());
                putNum(row, "rsi14", snap.rsi14());
                putNum(row, "drawdown60Pct", snap.drawdown60Pct());
                putNum(row, "bollingerPos", snap.bollingerPos());
                putNum(row, "volumeRatio", snap.volumeRatio());
                if (EtfPremiumClient.looksLikeEtf(symbol, snap.name())) {
                    EtfPremiumClient.PremiumQuote pq = premiumSession.lookup(symbol);
                    if (pq != null) {
                        premiums.put(symbol, pq);
                        putNum(row, "premiumPct", pq.getPremiumPct());
                        row.put("premiumBasis", pq.getPremiumBasis());
                    } else {
                        row.putNull("premiumPct");
                        row.put("premiumBasis", "UNAVAILABLE");
                    }
                }
                candidateArr.add(row);
            } catch (Exception e) {
                log.debug("候选剔除 {}: {}", symbol, e.getMessage());
                dropped.add(symbol + "(quote_fail)");
            }
        }

        ObjectNode root = objectMapper.createObjectNode();
        root.put("cash", account.getCash() == null ? 0 : account.getCash().doubleValue());
        ArrayNode posArr = root.putArray("positions");
        LocalDate today = LocalDate.now();
        for (PaperPosition p : positions) {
            ObjectNode n = posArr.addObject();
            n.put("symbol", p.getSymbol());
            n.put("qty", p.getQty() == null ? 0 : p.getQty());
            n.put("availableQty", p.getAvailableQty() == null ? 0 : p.getAvailableQty());
            n.put("avgCost", p.getAvgCost() == null ? 0 : p.getAvgCost().doubleValue());
            if (p.getTakeProfitPct() != null) {
                n.put("takeProfitPct", p.getTakeProfitPct().doubleValue());
            }
            if (p.getStopLossPct() != null) {
                n.put("stopLossPct", p.getStopLossPct().doubleValue());
            }
            if (p.getMaxHoldDays() != null) {
                n.put("maxHoldDays", p.getMaxHoldDays());
            }
            int holdDays = 0;
            if (p.getOpenedAt() != null) {
                holdDays = (int) ChronoUnit.DAYS.between(p.getOpenedAt().toLocalDate(), today);
            }
            n.put("holdDays", holdDays);
            try {
                BigDecimal last = tradeService.lastClose(p.getSymbol());
                n.put("lastPrice", last.doubleValue());
                if (p.getAvgCost() != null && p.getAvgCost().compareTo(BigDecimal.ZERO) > 0) {
                    double pnl = last.subtract(p.getAvgCost())
                            .divide(p.getAvgCost(), 6, RoundingMode.HALF_UP)
                            .doubleValue();
                    n.put("pnlPct", pnl);
                }
            } catch (Exception ignored) {
            }
        }
        ArrayNode cdArr = root.putArray("cooldowns");
        for (PaperCooldown c : cooldowns) {
            ObjectNode n = cdArr.addObject();
            n.put("symbol", c.getSymbol());
            n.put("untilDate", c.getUntilDate() == null ? "" : c.getUntilDate().toString());
        }
        root.set("candidates", candidateArr);
        ArrayNode dropArr = root.putArray("droppedCandidates");
        dropped.forEach(dropArr::add);

        // 近期决策摘要
        List<PaperAiDecision> recent = decisionMapper.selectList(new LambdaQueryWrapper<PaperAiDecision>()
                .orderByDesc(PaperAiDecision::getCreatedAt)
                .last("LIMIT 8"));
        ArrayNode recentArr = root.putArray("recentDecisions");
        for (PaperAiDecision d : recent) {
            ObjectNode n = recentArr.addObject();
            n.put("action", d.getAction());
            n.put("symbol", d.getSymbol());
            n.put("execStatus", d.getExecStatus());
            if (d.getConfidence() != null) {
                n.put("confidence", d.getConfidence().doubleValue());
            }
        }

        String json;
        try {
            json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            json = root.toString();
        }
        return new BuiltContext(json, positions, cooldownSymbols, premiums, account.getCash());
    }

    private static void putNum(ObjectNode n, String field, Double v) {
        if (v == null || !Double.isFinite(v)) {
            n.putNull(field);
        } else {
            n.put(field, v);
        }
    }

    /** 上下文构建结果。 */
    public record BuiltContext(
            String contextJson,
            List<PaperPosition> positions,
            Set<String> cooldownSymbols,
            Map<String, EtfPremiumClient.PremiumQuote> premiums,
            BigDecimal cash
    ) {
    }
}
