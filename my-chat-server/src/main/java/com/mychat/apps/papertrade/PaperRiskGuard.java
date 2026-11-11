package com.mychat.apps.papertrade;

import com.mychat.apps.market.EtfPremiumClient;
import com.mychat.apps.papertrade.entity.po.PaperAiDecision;
import com.mychat.apps.papertrade.entity.po.PaperPosition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 确定性风控闸门：仓位上限、冷却、ETF 溢价 fail-closed、同类型 ETF 限制。
 * LLM 建议可在此被拒。
 */
@Component
@RequiredArgsConstructor
public class PaperRiskGuard {

    private final PaperTradeProperties properties;

    /** 校验买入；通过返回 null，否则返回拒绝原因码。 */
    public String rejectBuyReason(PaperAiDecision d,
                                  List<PaperPosition> positions,
                                  Set<String> cooldownSymbols,
                                  EtfPremiumClient.PremiumQuote premium,
                                  BigDecimal cash) {
        if (d == null || !StringUtils.hasText(d.getSymbol())) {
            return "MISSING_SYMBOL";
        }
        String symbol = d.getSymbol().trim().toUpperCase(Locale.ROOT);
        if (cooldownSymbols != null && cooldownSymbols.contains(symbol)) {
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
        if (d.getQty() == null || d.getQty() <= 0) {
            return "INVALID_QTY";
        }
        BigDecimal ref = d.getRefPrice() == null ? BigDecimal.ZERO : d.getRefPrice();
        if (ref.compareTo(BigDecimal.ZERO) > 0 && cash != null) {
            BigDecimal notional = ref.multiply(BigDecimal.valueOf(d.getQty()));
            double maxAlloc = properties.getMaxSingleAllocation() <= 0 ? 0.95 : properties.getMaxSingleAllocation();
            // 相对当前现金的粗校验
            if (cash.compareTo(BigDecimal.ZERO) > 0
                    && notional.compareTo(cash.multiply(BigDecimal.valueOf(maxAlloc))) > 0) {
                return "MAX_SINGLE_ALLOCATION";
            }
        }
        boolean etf = EtfPremiumClient.looksLikeEtf(symbol, d.getName());
        if (etf && properties.isRequireEtfPremium()) {
            if (premium == null || premium.getPremiumPct() == null
                    || !Double.isFinite(premium.getPremiumPct())) {
                return "PREMIUM_UNAVAILABLE";
            }
            double max = EtfPremiumClient.looksLikeQdii(symbol, d.getName())
                    ? properties.getMaxQdiiPremiumPct()
                    : properties.getMaxEtfPremiumPct();
            if (premium.getPremiumPct() > max) {
                return "PREMIUM_TOO_HIGH";
            }
        }
        return null;
    }

    /** 校验卖出。 */
    public String rejectSellReason(PaperAiDecision d, List<PaperPosition> positions) {
        if (d == null || !StringUtils.hasText(d.getSymbol())) {
            return "MISSING_SYMBOL";
        }
        String symbol = d.getSymbol().trim().toUpperCase(Locale.ROOT);
        PaperPosition pos = positions.stream()
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
        if (!buyingEtf) {
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
}
