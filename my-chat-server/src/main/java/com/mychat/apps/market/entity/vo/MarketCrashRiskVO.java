package com.mychat.apps.market.entity.vo;

import lombok.Data;

/** 美股大回撤情景预警（按自然周缓存）。 */
@Data
public class MarketCrashRiskVO {

    /** ISO 年-周，如 2026-W37。 */
    private String weekKey;

    /** 最近一次成功或失败写入时间。 */
    private String generatedAt;

    /** 可能发生的时间窗口（数周到数月）。 */
    private String window;

    /** 可能的触发因素。 */
    private String trigger;

    /** 可能的影响。 */
    private String impact;

    /** RUNNING / SUCCEEDED / FAILED。 */
    private String status;

    /** 后台任务 id。 */
    private String jobId;

    /** 失败原因。 */
    private String errorMessage;
}
