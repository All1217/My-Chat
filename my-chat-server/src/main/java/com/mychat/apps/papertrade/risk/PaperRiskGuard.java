package com.mychat.apps.papertrade.risk;

import com.mychat.apps.market.EtfPremiumClient;
import com.mychat.apps.papertrade.PaperTradeProperties;
import com.mychat.apps.papertrade.trader.entity.po.PaperAiDecision;
import com.mychat.apps.papertrade.ledger.entity.po.PaperPosition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 确定性风控闸门。买入会先把股数砍到合法整手，再按第一条失败原因拒绝。
 */
@Component
@RequiredArgsConstructor
public class PaperRiskGuard {

    private final PaperTradeProperties properties;

    /**
     * 校验并可能缩小买入数量。通过返回 null，并把 d.qty 写成合法股数。
     */
    public String assessBuy(PaperAiDecision d, PaperGateContext ctx) {
        if (d == null || !StringUtils.hasText(d.getSymbol())) {
            return "MISSING_SYMBOL";
        }
        if (ctx != null && ctx.isBuyPaused()) {
            return "BUY_PAUSED";
        }
        String symbol = d.getSymbol().trim().toUpperCase(Locale.ROOT);
        List<PaperPosition> positions = ctx == null || ctx.getPositions() == null ? List.of() : ctx.getPositions();
        Set<String> cooldown = ctx == null || ctx.getCooldownSymbols() == null ? Set.of() : ctx.getCooldownSymbols();
        // 1. 结构约束：冷却、持仓数、同类型 ETF、禁止加仓、止损价
        if (cooldown.contains(symbol)) {
            return "IN_COOLDOWN";
        }
        int maxPos = properties.getMaxPositions() <= 0 ? 2 : properties.getMaxPositions();
        boolean alreadyHeld = positions.stream().anyMatch(p -> symbol.equalsIgnoreCase(p.getSymbol()));
        if (!alreadyHeld && positions.size() >= maxPos) {
            return "MAX_POSITIONS";
        }
        if (sameTypeEtfAlreadyHeld(symbol, d.getName(), positions) && !alreadyHeld) {
            return "SAME_TYPE_ETF";
        }
        if (alreadyHeld) {
            return "ADD_ON_FORBIDDEN";
        }
        if (d.getStopLossPct() == null || d.getStopLossPct().compareTo(BigDecimal.ZERO) <= 0) {
            return "MISSING_STOP";
        }
        if (d.getRefPrice() == null || d.getRefPrice().compareTo(BigDecimal.ZERO) <= 0) {
            return "MISSING_PRICE";
        }
        PaperSymbolFacts facts = ctx == null || ctx.getFacts() == null ? null : ctx.getFacts().get(symbol);
        String filtered = rejectByFacts(symbol, d.getName(), facts, ctx);
        if (filtered != null) {
            return filtered;
        }
        // 2. 股数取模型、现金、单票上限、风险预算的最小值
        BigDecimal cash = ctx == null || ctx.getCash() == null ? BigDecimal.ZERO : ctx.getCash();
        BigDecimal equity = ctx == null || ctx.getEquity() == null ? cash : ctx.getEquity();
        double budget = ctx == null || ctx.getRiskBudgetPct() <= 0
                ? properties.getRiskBudgetPct() : ctx.getRiskBudgetPct();
        Integer capped = capQty(d.getQty(), d.getRefPrice(), d.getStopLossPct(), cash, equity, budget);
        if (capped == null) {
            return "LOT_BELOW_MIN";
        }
        d.setQty(capped);
        // 3. 最低佣金相对预期利润；缩小股数只会更差，所以只拒绝
        return rejectCommissionRatio(d.getRefPrice(), capped, d.getTakeProfitPct());
    }

    /** 校验卖出。facts 为空时不拦跌停，避免行情缺失把持仓困住。 */
    public String rejectSellReason(PaperAiDecision d, List<PaperPosition> positions, PaperSymbolFacts facts) {
        if (d == null || !StringUtils.hasText(d.getSymbol())) {
            return "MISSING_SYMBOL";
        }
        String symbol = d.getSymbol().trim().toUpperCase(Locale.ROOT);
        if (facts != null && facts.limitDown()) {
            return "LIMIT_DOWN";
        }
        PaperPosition pos = positions == null ? null : positions.stream()
                .filter(p -> symbol.equalsIgnoreCase(p.getSymbol()))
                .findFirst()
                .orElse(null);
        if (pos == null) {
            return "NO_POSITION";
        }
        int avail = pos.getAvailableQty() == null ? 0 : pos.getAvailableQty();
        if (d.getQty() == null || d.getQty() <= 0) {
            return "INVALID_QTY";
        }
        if (d.getQty() > avail) {
            return "T1_AVAILABLE_QTY";
        }
        return null;
    }

    /** 同类型 ETF 启发式：已有一只 ETF 时再买另一只 ETF。 */
    static boolean sameTypeEtfAlreadyHeld(String symbol, String name, List<PaperPosition> positions) {
        boolean buyingEtf = EtfPremiumClient.looksLikeEtf(symbol, name);
        if (!buyingEtf || positions == null) {
            return false;
        }
        for (PaperPosition p : positions) {
            if (EtfPremiumClient.looksLikeEtf(p.getSymbol(), null)
                    && !symbol.equalsIgnoreCase(p.getSymbol())) {
                return true;
            }
        }
        return false;
    }

    private String rejectByFacts(String symbol, String name, PaperSymbolFacts facts, PaperGateContext ctx) {
        if (facts == null) {
            return "LIQUIDITY_UNKNOWN";
        }
        if (facts.st()) {
            return "ST";
        }
        if (facts.suspended()) {
            return "SUSPENDED";
        }
        if (facts.limitUp()) {
            return "LIMIT_UP";
        }
        int minBars = facts.etf()
                ? positive(properties.getMinListedBarsEtf(), 20)
                : positive(properties.getMinListedBarsStock(), 60);
        if (facts.barCount() < minBars) {
            return "LISTED_TOO_SHORT";
        }
        double minAmount = facts.etf()
                ? positiveDouble(properties.getMinAvgAmountEtf(), 30_000_000d)
                : positiveDouble(properties.getMinAvgAmountStock(), 50_000_000d);
        if (facts.avgAmount20() == null) {
            return "LIQUIDITY_UNKNOWN";
        }
        if (facts.avgAmount20() < minAmount) {
            return "LIQUIDITY";
        }
        if (ctx != null && ctx.isDrawdownRestricted()) {
            if (!facts.etf() || !StringUtils.hasText(ctx.getBestEtfSymbol())
                    || !symbol.equalsIgnoreCase(ctx.getBestEtfSymbol())) {
                return "DRAWDOWN_ETF_ONLY";
            }
        }
        if (facts.etf() && properties.isRequireEtfPremium()) {
            EtfPremiumClient.PremiumQuote premium = ctx == null || ctx.getPremiums() == null
                    ? null : ctx.getPremiums().get(symbol);
            if (premium == null || premium.getPremiumPct() == null
                    || !Double.isFinite(premium.getPremiumPct())) {
                return "PREMIUM_UNAVAILABLE";
            }
            double max = EtfPremiumClient.looksLikeQdii(symbol, name)
                    ? properties.getMaxQdiiPremiumPct()
                    : properties.getMaxEtfPremiumPct();
            if (premium.getPremiumPct() > max) {
                return "PREMIUM_TOO_HIGH";
            }
        }
        return null;
    }

    /** 现金能买的整手、95% 上限、风险预算股数，再向下取整到 100。不足 1 手返回 null。 */
    Integer capQty(Integer requested, BigDecimal refPrice, BigDecimal stopPct,
                   BigDecimal cash, BigDecimal equity, double budget) {
        if (cash == null) {
            cash = BigDecimal.ZERO;
        }
        if (equity == null) {
            equity = cash;
        }
        int byRequest = requested == null || requested <= 0 ? Integer.MAX_VALUE : floorLot(requested);
        int byCash = lotsAffordable(cash, refPrice);
        double maxAlloc = properties.getMaxSingleAllocation() <= 0 ? 0.95 : properties.getMaxSingleAllocation();
        int byAlloc = floorLot(cash.multiply(BigDecimal.valueOf(maxAlloc))
                .divide(refPrice, 0, RoundingMode.DOWN).intValue());
        BigDecimal riskCash = equity.multiply(BigDecimal.valueOf(budget));
        BigDecimal perShare = refPrice.multiply(stopPct);
        int byRisk = 0;
        if (perShare.compareTo(BigDecimal.ZERO) > 0) {
            byRisk = floorLot(riskCash.divide(perShare, 0, RoundingMode.DOWN).intValue());
        }
        int qty = min(byRequest, byCash, byAlloc, byRisk);
        return qty >= 100 ? qty : null;
    }

    private String rejectCommissionRatio(BigDecimal refPrice, int qty, BigDecimal takeProfit) {
        if (takeProfit == null || takeProfit.compareTo(BigDecimal.ZERO) <= 0) {
            return "MISSING_TAKE_PROFIT";
        }
        BigDecimal min = properties.getMinCommission() == null ? BigDecimal.ZERO : properties.getMinCommission();
        BigDecimal profit = refPrice.multiply(BigDecimal.valueOf(qty)).multiply(takeProfit);
        if (profit.compareTo(BigDecimal.ZERO) <= 0) {
            return "MISSING_TAKE_PROFIT";
        }
        double max = properties.getCommissionToProfitMax() <= 0 ? 0.20 : properties.getCommissionToProfitMax();
        if (min.divide(profit, 6, RoundingMode.HALF_UP).doubleValue() > max) {
            return "COMMISSION_RATIO";
        }
        return null;
    }

    private int lotsAffordable(BigDecimal cash, BigDecimal refPrice) {
        if (cash == null || cash.compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }
        int raw = floorLot(cash.divide(refPrice, 0, RoundingMode.DOWN).intValue());
        for (int q = raw; q >= 100; q -= 100) {
            BigDecimal notional = refPrice.multiply(BigDecimal.valueOf(q));
            if (notional.add(commission(notional)).compareTo(cash) <= 0) {
                return q;
            }
        }
        return 0;
    }

    private BigDecimal commission(BigDecimal notional) {
        BigDecimal rate = properties.getCommissionRate() == null ? BigDecimal.ZERO : properties.getCommissionRate();
        BigDecimal fee = notional.multiply(rate).setScale(4, RoundingMode.HALF_UP);
        BigDecimal min = properties.getMinCommission() == null ? BigDecimal.ZERO : properties.getMinCommission();
        if (fee.compareTo(min) < 0) {
            return min.setScale(4, RoundingMode.HALF_UP);
        }
        return fee;
    }

    private static int floorLot(int qty) {
        if (qty < 100) {
            return qty;
        }
        return qty - (qty % 100);
    }

    private static int min(int a, int b, int c, int d) {
        return Math.min(Math.min(a, b), Math.min(c, d));
    }

    private static int positive(int value, int fallback) {
        return value <= 0 ? fallback : value;
    }

    private static double positiveDouble(double value, double fallback) {
        return value <= 0 ? fallback : value;
    }
}
