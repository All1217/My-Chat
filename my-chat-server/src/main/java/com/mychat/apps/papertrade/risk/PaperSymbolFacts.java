package com.mychat.apps.papertrade.risk;

/** 一只候选标的的过滤事实，供上下文和风控闸门共用。 */
public record PaperSymbolFacts(
        String symbol,
        String name,
        boolean etf,
        boolean qdii,
        boolean st,
        boolean suspended,
        boolean limitUp,
        boolean limitDown,
        boolean stabilized,
        int barCount,
        Double avgAmount20
) {
}
