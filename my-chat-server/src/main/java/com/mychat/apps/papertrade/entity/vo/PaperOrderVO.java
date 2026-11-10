package com.mychat.apps.papertrade.entity.vo;

import lombok.Data;

import java.math.BigDecimal;

/** 订单一行。 */
@Data
public class PaperOrderVO {

    private String id;
    private String side;
    private String symbol;
    private String market;
    private Integer qty;
    private BigDecimal fillPrice;
    private String status;
    private String rejectReason;
    private String createdAt;
}
