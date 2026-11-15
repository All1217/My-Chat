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
}
