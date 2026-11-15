package com.mychat.apps.papertrade.ledger.entity.vo;

import lombok.Data;

import java.util.List;

/** 模拟炒股看板行情：基础指标 + 历史折线。 */
@Data
public class PaperQuoteVO {
    private String symbol;
    private String market;
    private String name;
    private String rangeKey;
    private Double lastPrice;
    private Double changePct;
    private Double high;
    private Double low;
    private Long volume;
    private Double amount;
    private Double turnoverRate;
    private List<PaperKlinePointVO> history;
}
