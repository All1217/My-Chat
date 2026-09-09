package com.mychat.apps.market.entity.vo;

import lombok.Data;

/** 已保存的策略与最近一次 AI 评价。 */
@Data
public class MarketStrategyVO {

    /** 策略正文，未保存过则为空。 */
    private String strategyText;

    /** AI 评价，从未评价过则为空。 */
    private String evaluation;

    /** 最近一次写入时间，ISO-8601；从未写入则为空。 */
    private String updatedAt;
}
