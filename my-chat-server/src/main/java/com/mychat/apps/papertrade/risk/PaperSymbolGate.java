package com.mychat.apps.papertrade.risk;

import com.mychat.apps.market.EtfPremiumClient;
import com.mychat.apps.market.entity.vo.KlinePointVO;
import com.mychat.apps.market.entity.vo.MarketQuoteVO;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/** 用日 K 判断 ST、停牌、上市根数、成交额、涨跌停和企稳。 */
@Component
public class PaperSymbolGate {

    private static final int AMOUNT_WINDOW = 20;
    private static final double LIMIT_BUFFER = 0.3;

    /** 行情缺失时返回空事实，成交额留空以便闸门 fail-closed。 */
    public PaperSymbolFacts analyze(MarketQuoteVO quote) {
        String symbol = quote == null || quote.getSymbol() == null ? "" : quote.getSymbol().trim();
        String name = quote == null ? "" : quote.getName();
        List<KlinePointVO> bars = quote == null || quote.getHistory() == null ? List.of() : quote.getHistory();
        boolean etf = EtfPremiumClient.looksLikeEtf(symbol, name);
        Double change = changePct(quote, bars);
        return new PaperSymbolFacts(
                symbol,
                name,
                etf,
                EtfPremiumClient.looksLikeQdii(symbol, name),
                looksSt(name),
                suspended(bars),
                change != null && change >= limitPct(symbol, name) - LIMIT_BUFFER,
                change != null && change <= -(limitPct(symbol, name) - LIMIT_BUFFER),
                stabilized(bars),
                bars.size(),
                avgAmount(bars)
        );
    }

    /** 板块涨跌幅上限（%）：ST 5，创业板/科创板 20，其余 10。 */
    static double limitPct(String symbol, String name) {
        if (looksSt(name)) {
            return 5.0;
        }
        String s = symbol == null ? "" : symbol.trim();
        if (s.startsWith("300") || s.startsWith("301") || s.startsWith("688")) {
            return 20.0;
        }
        return 10.0;
    }

    /** 名称里出现 ST（含 *ST）。 */
    static boolean looksSt(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        String n = name.toUpperCase(Locale.ROOT).replace("*", "").replace(" ", "");
        return n.contains("ST");
    }

    private static boolean suspended(List<KlinePointVO> bars) {
        if (bars.isEmpty()) {
            return false;
        }
        Long volume = bars.get(bars.size() - 1).getVolume();
        return volume != null && volume == 0L;
    }

    /** 收盘高于开盘，或高于前收。 */
    static boolean stabilized(List<KlinePointVO> bars) {
        if (bars.isEmpty()) {
            return false;
        }
        KlinePointVO last = bars.get(bars.size() - 1);
        if (last.getClose() == null) {
            return false;
        }
        if (last.getOpen() != null && last.getClose() > last.getOpen()) {
            return true;
        }
        if (bars.size() >= 2) {
            Double prev = bars.get(bars.size() - 2).getClose();
            return prev != null && last.getClose() > prev;
        }
        return false;
    }

    private static Double changePct(MarketQuoteVO quote, List<KlinePointVO> bars) {
        if (quote != null && quote.getChangePct() != null && Double.isFinite(quote.getChangePct())) {
            return quote.getChangePct();
        }
        if (bars.size() < 2) {
            return null;
        }
        Double prev = bars.get(bars.size() - 2).getClose();
        Double last = bars.get(bars.size() - 1).getClose();
        if (prev == null || last == null || prev == 0d) {
            return null;
        }
        return (last - prev) / prev * 100.0;
    }

    /** 最近 20 根里有成交额的均值；一根都没有则返回 null。 */
    static Double avgAmount(List<KlinePointVO> bars) {
        if (bars.isEmpty()) {
            return null;
        }
        int from = Math.max(0, bars.size() - AMOUNT_WINDOW);
        double sum = 0;
        int n = 0;
        for (int i = from; i < bars.size(); i++) {
            Double amount = bars.get(i).getAmount();
            if (amount == null || !Double.isFinite(amount)) {
                continue;
            }
            sum += amount;
            n++;
        }
        return n == 0 ? null : sum / n;
    }
}
