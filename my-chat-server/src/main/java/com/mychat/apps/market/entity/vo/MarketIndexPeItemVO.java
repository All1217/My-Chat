package com.mychat.apps.market.entity.vo;

import lombok.Data;

/** 一只指数的当前市盈率、近五年分位与一句短评。 */
@Data
public class MarketIndexPeItemVO {

    /** 东财指数代码，如 SPX。 */
    private String code;

    /** 展示名，如 标普500。 */
    private String name;

    /** 当前市盈率（优先 TTM）；缺测为 null。 */
    private Double pe;

    /** 近五年分位 0–100；样本不足时为 null。 */
    private Double percentile;

    /** 近五年有效 PE 样本数。 */
    private int sampleSize;

    /** 一句中文短评。 */
    private String comment;
}
