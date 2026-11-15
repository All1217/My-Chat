package com.mychat.apps.papertrade.ledger;

import com.mychat.apps.market.MarketQuoteClient;
import com.mychat.apps.market.entity.vo.KlinePointVO;
import com.mychat.apps.market.entity.vo.MarketQuoteVO;
import com.mychat.apps.papertrade.ledger.entity.vo.PaperKlinePointVO;
import com.mychat.apps.papertrade.ledger.entity.vo.PaperQuoteVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 模拟炒股看板行情：包装东财客户端，限制本模块可用档位。 */
@Service
@RequiredArgsConstructor
public class PaperQuoteService {

    private static final Set<String> RANGES = Set.of("1M", "3M", "1Y", "3Y", "5Y", "ALL");

    private final MarketQuoteClient quoteClient;

    /** 拉取行情；range 缺省 1M。 */
    public PaperQuoteVO quote(String symbol, String range) {
        if (!StringUtils.hasText(symbol)) {
            throw new IllegalArgumentException("请输入股票代码");
        }
        String rangeKey = normalizeRange(range);
        MarketQuoteVO src = quoteClient.fetch(symbol.trim(), rangeKey);
        return toPaper(src);
    }

    /** 仅允许看板档位。 */
    static String normalizeRange(String range) {
        String key = range == null || range.isBlank() ? "1M" : range.trim().toUpperCase(Locale.ROOT);
        if (!RANGES.contains(key)) {
            throw new IllegalArgumentException("时间段仅支持 1M / 3M / 1Y / 3Y / 5Y / ALL");
        }
        return key;
    }

    private static PaperQuoteVO toPaper(MarketQuoteVO src) {
        PaperQuoteVO vo = new PaperQuoteVO();
        vo.setSymbol(src.getSymbol());
        vo.setMarket(src.getMarket());
        vo.setName(src.getName());
        vo.setRangeKey(src.getRangeKey());
        vo.setLastPrice(src.getLastPrice());
        vo.setChangePct(src.getChangePct());
        vo.setHigh(src.getHigh());
        vo.setLow(src.getLow());
        vo.setVolume(src.getVolume());
        vo.setAmount(src.getAmount());
        vo.setTurnoverRate(src.getTurnoverRate());
        List<PaperKlinePointVO> history = new ArrayList<>();
        if (src.getHistory() != null) {
            for (KlinePointVO bar : src.getHistory()) {
                history.add(toPoint(bar));
            }
        }
        vo.setHistory(history);
        return vo;
    }

    private static PaperKlinePointVO toPoint(KlinePointVO bar) {
        PaperKlinePointVO p = new PaperKlinePointVO();
        p.setDate(bar.getDate());
        p.setOpen(bar.getOpen());
        p.setClose(bar.getClose());
        p.setHigh(bar.getHigh());
        p.setLow(bar.getLow());
        p.setVolume(bar.getVolume());
        p.setAmount(bar.getAmount());
        p.setTurnoverRate(bar.getTurnoverRate());
        return p;
    }
}
