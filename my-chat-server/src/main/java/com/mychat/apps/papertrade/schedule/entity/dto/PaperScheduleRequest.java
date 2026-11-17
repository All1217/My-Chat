package com.mychat.apps.papertrade.schedule.entity.dto;

import lombok.Data;

/** 更新定时任务配置。 */
@Data
public class PaperScheduleRequest {
    private Boolean researchEnabled;
    private String researchCron;
    private Boolean traderEnabled;
    private String traderCron;
    private String zoneId;
    /** 是否纳入自选。 */
    private Boolean includeWatchlist;

    /** 是否启用工具检索。 */
    private Boolean researchToolSearchEnabled;

    private Boolean cleanupEnabled;
    private String cleanupCron;
    private Boolean analystEnabled;
    private String analystCron;
    /** 价值等级=2 滞留天数。 */
    private Integer warehouseTier2RetainDays;
}
