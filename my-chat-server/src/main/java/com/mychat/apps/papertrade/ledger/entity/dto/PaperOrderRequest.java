package com.mychat.apps.papertrade.ledger.entity.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 市价下单请求。 */
@Data
public class PaperOrderRequest {

    private String symbol;
    private String side;
    private Integer qty;

    /** MANUAL / AI / EXIT，默认 MANUAL。 */
    private String source;

    /** 关联 AI 决策 ID。 */
    private String decisionId;

    /** 买入后写入持仓的止盈比例。 */
    private BigDecimal takeProfitPct;

    /** 买入后写入持仓的止损比例。 */
    private BigDecimal stopLossPct;

    /** 买入后写入持仓的最长持有天数。 */
    private Integer maxHoldDays;

    /** 卖出后冷却天数；缺省按标的类型默认。 */
    private Integer cooldownDays;

    /** 卖出原因：STOP_LOSS / TAKE_PROFIT / MAX_HOLD / AI / MANUAL。 */
    private String exitReason;
}
