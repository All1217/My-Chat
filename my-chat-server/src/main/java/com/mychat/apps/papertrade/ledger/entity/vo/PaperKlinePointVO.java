package com.mychat.apps.papertrade.ledger.entity.vo;

import lombok.Data;

/** 模拟炒股看板用的一根 K 线。 */
@Data
public class PaperKlinePointVO {
    private String date;
    private Double open;
    private Double close;
    private Double high;
    private Double low;
    private Long volume;
    private Double amount;
    private Double turnoverRate;
}
