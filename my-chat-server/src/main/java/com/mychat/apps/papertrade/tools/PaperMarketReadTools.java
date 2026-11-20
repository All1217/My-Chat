package com.mychat.apps.papertrade.tools;

import com.mychat.apps.market.MarketDipIndicators;
import com.mychat.apps.market.MarketQuoteClient;
import com.mychat.apps.market.entity.vo.MarketQuoteVO;
import com.mychat.apps.papertrade.risk.PaperSymbolFacts;
import com.mychat.apps.papertrade.risk.PaperSymbolGate;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** 搜集/交易共用的行情只读工具。 */
@Component
public class PaperMarketReadTools {

    private final MarketQuoteClient quoteClient;
    private final PaperSymbolGate symbolGate;

    public PaperMarketReadTools(MarketQuoteClient quoteClient, PaperSymbolGate symbolGate) {
        this.quoteClient = quoteClient;
        this.symbolGate = symbolGate;
    }

    /** 读取日 K 与基础行情摘要。 */
    @Tool(description = "读取标的日 K 与价格摘要。参数 symbol 如 600519，range 默认 1Y")
    public String getQuote(
            @ToolParam(description = "股票或 ETF 代码") String symbol,
            @ToolParam(description = "时间段：1M/3M/6M/1Y/3Y，默认 1Y", required = false) String range) {
        return call(() -> {
            MarketQuoteVO quote = quoteClient.fetch(symbol, range == null ? "1Y" : range);
            MarketDipIndicators.Snapshot snap = MarketDipIndicators.from(quote);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("symbol", quote.getSymbol());
            out.put("name", quote.getName());
            out.put("lastPrice", snap.lastPrice());
            out.put("changePct", snap.changePct());
            out.put("vsMa20Pct", snap.vsMa20Pct());
            out.put("drawdown60Pct", snap.drawdown60Pct());
            boolean belowMa = snap.vsMa20Pct() != null && snap.vsMa20Pct() < 0;
            boolean drawn = snap.drawdown60Pct() != null && snap.drawdown60Pct() <= -8;
            out.put("relativelyLow", belowMa || drawn);
            out.put("barCount", quote.getHistory() == null ? 0 : quote.getHistory().size());
            return out;
        });
    }

    /** 读取 ST/停牌/涨跌停/流动性/企稳等事实。 */
    @Tool(description = "读取标的事实：ST、停牌、涨跌停、上市根数、流动性、企稳")
    public String getSymbolFacts(@ToolParam(description = "股票或 ETF 代码") String symbol) {
        return call(() -> {
            MarketQuoteVO quote = quoteClient.fetch(symbol, "1Y");
            PaperSymbolFacts facts = symbolGate.analyze(quote);
            MarketDipIndicators.Snapshot snap = MarketDipIndicators.from(quote);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("symbol", facts.symbol());
            out.put("name", facts.name());
            out.put("etf", facts.etf());
            out.put("st", facts.st());
            out.put("suspended", facts.suspended());
            out.put("limitUp", facts.limitUp());
            out.put("limitDown", facts.limitDown());
            out.put("stabilized", facts.stabilized());
            out.put("vsMa20Pct", snap.vsMa20Pct());
            out.put("drawdown60Pct", snap.drawdown60Pct());
            boolean belowMa = snap.vsMa20Pct() != null && snap.vsMa20Pct() < 0;
            boolean drawn = snap.drawdown60Pct() != null && snap.drawdown60Pct() <= -8;
            out.put("relativelyLow", belowMa || drawn);
            out.put("barCount", facts.barCount());
            out.put("avgAmount20", facts.avgAmount20());
            return out;
        });
    }

    private String call(java.util.function.Supplier<Object> action) {
        try {
            return PaperToolJson.write(action.get());
        } catch (Exception e) {
            return PaperToolJson.fail(e);
        }
    }
}
