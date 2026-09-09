package com.mychat.apps.market.entity.dto;

import lombok.Data;

import java.util.List;

/** 覆盖保存自选股列表。 */
@Data
public class MarketWatchlistRequest {

    /** 原始代码，最多 8 只。 */
    private List<String> symbols;
}
