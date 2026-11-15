package com.mychat.apps.papertrade.schedule.entity.vo;

import lombok.Data;

/** 定时任务配置视图。 */
@Data
public class PaperScheduleVO {
    private Boolean researchEnabled;
    private String researchCron;
    private Boolean traderEnabled;
    private String traderCron;
    private String zoneId;
    private Boolean includeWatchlist;
    private Boolean researchToolSearchEnabled;
    private String updatedAt;
}
