package com.mychat.apps.papertrade.evaluator.entity.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 交易评估员一轮。 */
@TableName("paper_trade_evaluator_run")
@Data
public class PaperTradeEvaluatorRun {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCEEDED = "SUCCEEDED";
    public static final String STATUS_FAILED = "FAILED";

    public static final String TRIGGER_WEEKLY = "WEEKLY";
    public static final String TRIGGER_LIQUIDITY_RESET = "LIQUIDITY_RESET";

    @TableId(type = IdType.INPUT)
    private String id;

    @TableField("job_id")
    private String jobId;

    private String status;

    @TableField("trigger_type")
    private String triggerType;

    @TableField("stage_summary_id")
    private String stageSummaryId;

    @TableField("email_status")
    private String emailStatus;

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
