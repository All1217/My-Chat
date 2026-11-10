package com.mychat.apps.papertrade.entity.dto;

import lombok.Data;

/** 市价下单请求。 */
@Data
public class PaperOrderRequest {

    /** 股票代码，如 600519 / AAPL。 */
    private String symbol;

    /** BUY 或 SELL。 */
    private String side;

    /** 股数；A 股须为 100 的整数倍。 */
    private Integer qty;
}
