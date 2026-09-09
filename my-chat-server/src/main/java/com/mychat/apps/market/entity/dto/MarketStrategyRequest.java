package com.mychat.apps.market.entity.dto;

import lombok.Data;

/** 保存投资策略正文。 */
@Data
public class MarketStrategyRequest {

    /** 用户输入的策略说明。 */
    private String strategyText;
}
