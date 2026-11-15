package com.mychat.apps.papertrade.schedule.entity.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/** 模拟炒股定时任务配置（单行）。 */
@TableName("paper_schedule_settings")
@Data
public class PaperScheduleSettings {

    public static final String DEFAULT_ID = "default";

    @TableId(type = IdType.INPUT)
    private String id;

    @TableField("research_enabled")
    private Boolean researchEnabled;

    @TableField("research_cron")
    private String researchCron;

    @TableField("trader_enabled")
    private Boolean traderEnabled;

    @TableField("trader_cron")
    private String traderCron;

    @TableField("zone_id")
    private String zoneId;

    /** 是否纳入自选：是则自选优先，否则交易员看不到自选。 */
    @TableField("include_watchlist")
    private Boolean includeWatchlist;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updatedAt;
}
