package com.mychat.apps.market.entity.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 三大美股指数市盈率与近五年分位（按自然日缓存）。 */
@Data
public class MarketIndexPeVO {

    /** 缓存对应的自然日，yyyy-MM-dd。 */
    private String cacheDate;

    /** 最近一次写入时间。 */
    private String generatedAt;

    /** SUCCEEDED / FAILED。 */
    private String status;

    /** 失败原因；成功时为空。 */
    private String errorMessage;

    /** 标普500、道琼斯、纳斯达克100。 */
    private List<MarketIndexPeItemVO> indices = new ArrayList<>();
}
