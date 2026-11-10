package com.mychat.apps.papertrade.entity.vo;

import lombok.Data;

import java.math.BigDecimal;

/** 模拟账户概览。 */
@Data
public class PaperAccountVO {

    private String id;
    private String name;
    private BigDecimal cash;
    private BigDecimal initialCash;
}
