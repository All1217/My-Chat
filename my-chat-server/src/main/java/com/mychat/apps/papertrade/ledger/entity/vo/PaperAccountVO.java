package com.mychat.apps.papertrade.ledger.entity.vo;

import lombok.Data;

import java.math.BigDecimal;

/** 模拟账户概览。 */
@Data
public class PaperAccountVO {

    private String id;
    private String name;
    private BigDecimal cash;
    private BigDecimal initialCash;
    private BigDecimal equity;
    private BigDecimal peakEquity;
    private BigDecimal drawdownPct;
    private Integer lossStreak;
    private String buyPauseUntil;
    private Boolean drawdownRestricted;
    private String latestSummary;
}
