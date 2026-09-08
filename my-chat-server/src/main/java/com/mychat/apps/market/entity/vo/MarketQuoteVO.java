package com.mychat.apps.market.entity.vo;

import lombok.Data;

import java.util.List;

/** 同步行情：基本信息 + 历史 K 线，不落库。 */
@Data
public class MarketQuoteVO {

    private String symbol;
    private String market;
    private String name;
    private String rangeKey;
    private Double lastPrice;
    private Double changePct;
    private Double high;
    private Double low;
    private Long volume;
    private List<KlinePointVO> history;
}
