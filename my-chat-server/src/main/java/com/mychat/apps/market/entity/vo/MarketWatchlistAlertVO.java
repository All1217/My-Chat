package com.mychat.apps.market.entity.vo;

import lombok.Data;

/** 当天抄底提醒：零只或一只。 */
@Data
public class MarketWatchlistAlertVO {

    /** 被点名的代码，空表示暂不提醒。 */
    private String pickSymbol;

    /** 被点名的名称。 */
    private String pickName;

    /** 一两句理由，或「暂无加仓提醒」。 */
    private String reason;

    /** 是否命中当日缓存。 */
    private boolean cached;
}
