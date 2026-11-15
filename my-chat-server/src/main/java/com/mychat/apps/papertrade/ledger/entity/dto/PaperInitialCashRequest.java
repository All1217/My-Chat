package com.mychat.apps.papertrade.ledger.entity.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 修改模拟账户初始资金。 */
@Data
public class PaperInitialCashRequest {
    private BigDecimal initialCash;
}
