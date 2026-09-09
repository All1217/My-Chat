package com.mychat.apps.market.entity.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 一根日 K：图上一个点。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KlinePointVO {

    private String date;
    private Double open;
    private Double close;
    private Double high;
    private Double low;
    private Long volume;
}
