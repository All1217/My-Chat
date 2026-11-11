package com.mychat.apps.papertrade.entity.vo;

import lombok.Data;

import java.math.BigDecimal;

/** 持仓一行。 */
@Data
public class PaperPositionVO {

    private String symbol;
    private String market;
    private Integer qty;
    private Integer availableQty;
    private BigDecimal avgCost;
    private BigDecimal marketValue;
    private BigDecimal takeProfitPct;
    private BigDecimal stopLossPct;
    private Integer maxHoldDays;
    private String openedAt;
}
