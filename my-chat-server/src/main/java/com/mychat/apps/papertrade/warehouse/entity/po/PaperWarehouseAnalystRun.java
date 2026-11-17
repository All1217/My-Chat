package com.mychat.apps.papertrade.warehouse.entity.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 票仓分析员一轮（Agent 深搜调等级）。 */
@TableName("paper_warehouse_analyst_run")
@Data
public class PaperWarehouseAnalystRun {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCEEDED = "SUCCEEDED";
    public static final String STATUS_FAILED = "FAILED";

    @TableId(type = IdType.INPUT)
    private String id;

    @TableField("job_id")
    private String jobId;

    private String status;

    @TableField("screened_count")
    private Integer screenedCount;

    @TableField("adjusted_count")
    private Integer adjustedCount;

    @TableField("stats_json")
    private String statsJson;

    @TableField("framework_trace")
    private String frameworkTrace;

    @TableField("error_message")
    private String errorMessage;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField("finished_at")
    private LocalDateTime finishedAt;
}
