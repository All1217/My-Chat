package com.mychat.apps.market.entity.vo;

import lombok.Data;

import java.util.List;

/** 一次预测任务的查询结果。 */
@Data
public class MarketForecastVO {

    private String id;
    private String jobId;
    private String status;
    private String symbol;
    private String market;
    private String rangeKey;
    private String name;
    private Double lastPrice;
    private Double changePct;
    private Double high;
    private Double low;
    private Long volume;
    private List<KlinePointVO> history;
    private List<KlinePointVO> forecast;
    private String summary;
    private String errorMessage;
}
