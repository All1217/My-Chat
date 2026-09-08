package com.mychat.apps.market.entity.dto;

import lombok.Data;

/** 提交走势预测：代码 + 时间段。 */
@Data
public class MarketForecastRequest {

    /** 原始输入，如 600519 / AAPL。 */
    private String symbol;

    /** 1M / 3M / 6M / 1Y / 3Y / 5Y / 10Y / ALL。 */
    private String range;
}
