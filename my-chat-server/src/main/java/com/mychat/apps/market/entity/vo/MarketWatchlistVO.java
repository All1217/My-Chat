package com.mychat.apps.market.entity.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 已保存的自选股代码。 */
@Data
public class MarketWatchlistVO {

    /** 规范化后的代码列表。 */
    private List<String> symbols = new ArrayList<>();
}
