package com.mychat.apps.papertrade.risk;

import com.mychat.apps.market.EtfPremiumClient;
import com.mychat.apps.papertrade.ledger.entity.po.PaperPosition;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 一轮决策共用的闸门输入：持仓、资金、过滤事实。 */
@Data
public class PaperGateContext {

    private List<PaperPosition> positions;
    private Set<String> cooldownSymbols;
    private Map<String, EtfPremiumClient.PremiumQuote> premiums;
    private Map<String, PaperSymbolFacts> facts;
    private BigDecimal cash;
    private BigDecimal equity;
    private boolean buyPaused;
    private boolean drawdownRestricted;
    private String bestEtfSymbol;
    private double riskBudgetPct;
}
