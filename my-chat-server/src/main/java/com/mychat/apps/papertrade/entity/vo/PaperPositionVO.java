package com.mychat.apps.papertrade.entity.vo;

import lombok.Data;

import java.math.BigDecimal;

/** 持仓一行。 */
@Data
public class PaperPositionVO {

    private String symbol;
    private String market;
    private Integer qty;
    private BigDecimal avgCost;
    private BigDecimal marketValue;
}
