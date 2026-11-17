package com.mychat.entity.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mychat.mapper.typehandler.JsonbListTypeHandler;
import com.mychat.mapper.typehandler.JsonbMapTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Agent 内部 Trace Step/Span 持久化实体。 */
@Data
@TableName(value = "agent_run_step", autoResultMap = true)
public class AgentRunStep {

    @TableId(type = IdType.INPUT)
    private String id;
    @TableField("run_id")
    private String runId;
    @TableField("sequence_no")
    private Long sequenceNo;
    @TableField("checkpoint_index")
    private Integer checkpointIndex;
    @TableField("parent_step_id")
    private String parentStepId;
    @TableField("step_type")
    private String stepType;
    @TableField("action_id")
    private String actionId;
    @TableField("tool_name")
    private String toolName;
    @TableField("display_name")
    private String displayName;
    private String status;
    @TableField("idempotency_key")
    private String idempotencyKey;
    @TableField("input_summary")
    private String inputSummary;
    @TableField("output_summary")
    private String outputSummary;
    @TableField(value = "citations", typeHandler = JsonbListTypeHandler.class)
    private List<Object> citations;
    @TableField(value = "metadata", typeHandler = JsonbMapTypeHandler.class)
    private Map<String, Object> metadata;
    @TableField("input_tokens")
    private Long inputTokens;
    @TableField("output_tokens")
    private Long outputTokens;
    @TableField("elapsed_ms")
    private Long elapsedMs;
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
