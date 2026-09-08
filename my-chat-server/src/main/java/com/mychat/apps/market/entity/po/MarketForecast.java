package com.mychat.apps.market.entity.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/** 股市分析一次预测任务行。 */
@TableName(value = "market_forecast")
@Data
public class MarketForecast {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCEEDED = "SUCCEEDED";
    public static final String STATUS_FAILED = "FAILED";

    @TableId(type = IdType.INPUT)
    private String id;

    @TableField(value = "symbol")
    private String symbol;

    @TableField(value = "market")
    private String market;

    @TableField(value = "range_key")
    private String rangeKey;

    @TableField(value = "name")
    private String name;

    @TableField(value = "history_json")
    private String historyJson;

    @TableField(value = "forecast_json")
    private String forecastJson;

    @TableField(value = "summary")
    private String summary;

    @TableField(value = "job_id")
    private String jobId;

    @TableField(value = "status")
    private String status;

    @TableField(value = "error_message")
    private String errorMessage;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updatedAt;
}
