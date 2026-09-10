package com.mychat.apps.market;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 股市策略落盘路径（本地 JSON，不建表）。 */
@Data
@Component
@ConfigurationProperties(prefix = "app.market")
public class MarketStrategyProperties {

    /** 相对后端工作目录，默认 ./data/market-strategy.json。 */
    private String strategyFile = "./data/market-strategy.json";

    /** 自选股与当日提醒缓存。 */
    private String watchlistFile = "./data/market-watchlist.json";

    /** 美股大回撤情景预警（按 ISO 周缓存）。 */
    private String crashRiskFile = "./data/market-crash-risk.json";

    /** 三大指数市盈率与五年分位（按自然日缓存）。 */
    private String indexPeFile = "./data/market-index-pe.json";
}
