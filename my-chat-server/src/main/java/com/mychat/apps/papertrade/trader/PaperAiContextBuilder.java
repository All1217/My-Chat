package com.mychat.apps.papertrade.trader;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mychat.apps.market.EtfPremiumClient;
import com.mychat.apps.market.MarketDipIndicators;
import com.mychat.apps.market.MarketQuoteClient;
import com.mychat.apps.market.entity.vo.MarketQuoteVO;
import com.mychat.apps.papertrade.PaperTradeProperties;
import com.mychat.apps.papertrade.ledger.PaperTradeService;
import com.mychat.apps.papertrade.risk.PaperSymbolFacts;
import com.mychat.apps.papertrade.risk.PaperSymbolGate;
import com.mychat.apps.papertrade.research.entity.po.PaperResearchNote;
import com.mychat.apps.papertrade.research.mapper.PaperResearchNoteMapper;
import com.mychat.apps.papertrade.ledger.entity.po.PaperAccount;
import com.mychat.apps.papertrade.trader.entity.po.PaperAiDecision;
import com.mychat.apps.papertrade.ledger.entity.po.PaperCooldown;
import com.mychat.apps.papertrade.ledger.entity.po.PaperPosition;
import com.mychat.apps.papertrade.risk.entity.po.PaperStageSummary;
import com.mychat.apps.papertrade.trader.mapper.PaperAiDecisionMapper;
import com.mychat.apps.papertrade.risk.mapper.PaperStageSummaryMapper;
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
    private final PaperResearchNoteMapper noteMapper;
    private final MarketQuoteClient quoteClient;
    private final EtfPremiumClient premiumClient;
    private final PaperAiDecisionMapper decisionMapper;
    private final PaperStageSummaryMapper stageMapper;
    private final PaperSymbolGate symbolGate;
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
            if (StringUtils.hasText(p.getSymbol())) {
                candidates.add(p.getSymbol().trim().toUpperCase(Locale.ROOT));
            }
        }
        int noteLimit = Math.max(1, properties.getResearchContextLimit());
        List<PaperResearchNote> notes = noteMapper.selectList(new LambdaQueryWrapper<PaperResearchNote>()
                .eq(PaperResearchNote::getKept, true)
                .orderByDesc(PaperResearchNote::getUpdatedAt)
                .last("LIMIT " + noteLimit));
        Map<String, PaperResearchNote> noteBySymbol = new LinkedHashMap<>();
        for (PaperResearchNote note : notes) {
            if (!StringUtils.hasText(note.getSymbol())) {
                continue;
            }
            String symbol = note.getSymbol().trim().toUpperCase(Locale.ROOT);
            noteBySymbol.put(symbol, note);
            candidates.add(symbol);
        }

        EtfPremiumClient.Session premiumSession = premiumClient.newSession();
        Map<String, EtfPremiumClient.PremiumQuote> premiums = new LinkedHashMap<>();
        Map<String, PaperSymbolFacts> facts = new LinkedHashMap<>();
        ArrayNode candidateArr = objectMapper.createArrayNode();
        List<String> dropped = new ArrayList<>();
        BigDecimal cheapestLot = null;
        boolean quoted = false;

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
                PaperSymbolFacts fact = symbolGate.analyze(quote);
                String factKey = snap.symbol() == null ? symbol.trim().toUpperCase(Locale.ROOT)
                        : snap.symbol().trim().toUpperCase(Locale.ROOT);
                facts.put(factKey, fact);
                quoted = true;
                if (snap.lastPrice() != null && snap.lastPrice() > 0) {
                    try {
                        BigDecimal lotCost = tradeService.buyLotCost(snap.symbol(),
                                BigDecimal.valueOf(snap.lastPrice()));
                        if (lotCost != null && (cheapestLot == null || lotCost.compareTo(cheapestLot) < 0)) {
                            cheapestLot = lotCost;
                        }
                    } catch (Exception e) {
                        log.debug("一手成本计算失败 {}: {}", symbol, e.getMessage());
                    }
                }
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
                row.put("stabilized", fact.stabilized());
                row.put("st", fact.st());
                row.put("suspended", fact.suspended());
                row.put("limitUp", fact.limitUp());
                row.put("limitDown", fact.limitDown());
                row.put("barCount", fact.barCount());
                putNum(row, "avgAmount20", fact.avgAmount20());
                PaperResearchNote note = noteBySymbol.get(factKey);
                if (note != null) {
                    row.put("story", note.getStory() == null ? "" : note.getStory());
                    row.put("risks", note.getRisks() == null ? "" : note.getRisks());
                    row.put("sourceTitles", note.getSourcesJson() == null ? "" : note.getSourcesJson());
                    if (note.getUpdatedAt() != null) {
                        row.put("noteUpdatedAt", note.getUpdatedAt().toString());
                    }
                }
                if (EtfPremiumClient.looksLikeEtf(symbol, snap.name())) {
                    EtfPremiumClient.PremiumQuote pq = premiumSession.lookup(symbol, snap.lastPrice());
                    if (pq != null) {
                        premiums.put(factKey, pq);
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
        String bestEtf = bestLiquidEtf(facts);
        if (bestEtf != null) {
            root.put("bestEtfSymbol", bestEtf);
        }
        PaperStageSummary latest = stageMapper.selectOne(new LambdaQueryWrapper<PaperStageSummary>()
                .orderByDesc(PaperStageSummary::getCreatedAt)
                .last("LIMIT 1"));
        if (latest != null && StringUtils.hasText(latest.getSummaryText())) {
            root.put("lastStageSummary", latest.getSummaryText());
        }

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
        return new BuiltContext(json, positions, cooldownSymbols, premiums, facts,
                bestEtf, cheapestLot, quoted, account.getCash(), candidates);
    }

    /** 成交额最高、且没有停牌的 ETF。 */
    private static String bestLiquidEtf(Map<String, PaperSymbolFacts> facts) {
        String best = null;
        double bestAmount = -1;
        for (PaperSymbolFacts fact : facts.values()) {
            if (!fact.etf() || fact.suspended() || fact.avgAmount20() == null) {
                continue;
            }
            if (fact.avgAmount20() > bestAmount) {
                bestAmount = fact.avgAmount20();
                best = fact.symbol();
            }
        }
        return best;
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
            Map<String, PaperSymbolFacts> facts,
            String bestEtfSymbol,
            BigDecimal cheapestLotCost,
            boolean quoteDataAvailable,
            BigDecimal cash,
            Set<String> candidateSymbols
    ) {
    }
}
