package com.mychat.apps.papertrade.trader.entity.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/** AI 交易一轮运行记录。 */
@TableName("paper_ai_run")
@Data
public class PaperAiRun {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCEEDED = "SUCCEEDED";
    public static final String STATUS_FAILED = "FAILED";

    @TableId(type = IdType.INPUT)
    private String id;

    @TableField("job_id")
    private String jobId;

    @TableField("status")
    private String status;

    @TableField("context_json")
    private String contextJson;

    @TableField("raw_response")
    private String rawResponse;

    @TableField("error_message")
    private String errorMessage;

    @TableField("framework_trace")
    private String frameworkTrace;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdAt;

    @TableField("finished_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime finishedAt;
}
