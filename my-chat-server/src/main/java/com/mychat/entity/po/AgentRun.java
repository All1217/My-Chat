package com.mychat.entity.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mychat.mapper.typehandler.JsonbMapTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/** 通用 Agent 运行持久化实体。 */
@Data
@TableName(value = "agent_run", autoResultMap = true)
public class AgentRun {

    @TableId(type = IdType.INPUT)
    private String id;
    private String scenario;
    @TableField("profile_version")
    private String profileVersion;
    @TableField("business_ref")
    private String businessRef;
    @TableField("conversation_id")
    private String conversationId;
    @TableField("user_id")
    private String userId;
    @TableField("async_job_id")
    private String asyncJobId;
    @TableField("idempotency_key")
    private String idempotencyKey;
    private String status;
    @TableField("current_step")
    private Integer currentStep;
    @TableField("checkpoint_version")
    private Integer checkpointVersion;
    @TableField(value = "checkpoint", typeHandler = JsonbMapTypeHandler.class)
    private Map<String, Object> checkpoint;
    @TableField("input_summary")
    private String inputSummary;
    @TableField("output_summary")
    private String outputSummary;
    @TableField("model_name")
    private String modelName;
    @TableField("input_tokens")
    private Long inputTokens;
    @TableField("output_tokens")
    private Long outputTokens;
    @TableField("total_tokens")
    private Long totalTokens;
    @TableField("error_message")
    private String errorMessage;
    @TableField("started_at")
    private LocalDateTime startedAt;
    @TableField("completed_at")
    private LocalDateTime completedAt;
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
