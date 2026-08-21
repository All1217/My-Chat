package com.mychat.entity.po;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "通用异步任务")
@TableName(value = "async_job")
@Data
public class AsyncJob {
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCEEDED = "SUCCEEDED";
    public static final String STATUS_FAILED = "FAILED";

    @Schema(description = "任务 ID")
    @TableId(type = IdType.INPUT)
    private String id;

    @Schema(description = "任务类型")
    @TableField(value = "job_type")
    private String jobType;

    @Schema(description = "PENDING / RUNNING / SUCCEEDED / FAILED")
    @TableField(value = "status")
    private String status;

    @Schema(description = "通知标题")
    @TableField(value = "title")
    private String title;

    @Schema(description = "业务主键（可空）")
    @TableField(value = "ref_id")
    private String refId;

    @Schema(description = "Handler JSON 入参")
    @TableField(value = "payload")
    private String payload;

    @Schema(description = "失败原因")
    @TableField(value = "error_message")
    private String errorMessage;

    @Schema(description = "创建时间")
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updatedAt;

    @Schema(description = "终态时间")
    @TableField(value = "finished_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime finishedAt;

    public static boolean isTerminal(String status) {
        return STATUS_SUCCEEDED.equals(status) || STATUS_FAILED.equals(status);
    }
}
