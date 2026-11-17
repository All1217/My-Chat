package com.mychat.apps.papertrade.guardrail;

import com.mychat.apps.papertrade.research.PaperResearchService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 模拟炒股领域输出校验；拒绝候选池外与证据不足的 kept 卡片。 */
@Component
public class PaperTradeGuardrails {

    /** 过滤搜集卡片：池外剔除；kept 必须有故事与证据分。 */
    public List<PaperResearchService.Card> validateCards(
            List<PaperResearchService.Card> cards, Set<String> candidateSymbols) {
        List<PaperResearchService.Card> out = new ArrayList<>();
        for (PaperResearchService.Card card : cards == null ? List.<PaperResearchService.Card>of() : cards) {
            if (card == null || !StringUtils.hasText(card.symbol)) {
                continue;
            }
            String symbol = card.symbol.trim().toUpperCase(Locale.ROOT);
            if (candidateSymbols != null && !candidateSymbols.contains(symbol)) {
                continue;
            }
            card.symbol = symbol;
            if (Boolean.TRUE.equals(card.kept)) {
                if (!StringUtils.hasText(card.story)
                        || card.evidenceScore == null
                        || card.evidenceScore < 0.4
                        || card.fitScore == null
                        || card.fitScore < 0) {
                    card.kept = false;
                    if (!StringUtils.hasText(card.rejectReason)) {
                        card.rejectReason = "证据不足或评分缺失";
                    }
                }
            }
            out.add(card);
        }
        return out;
    }
}
