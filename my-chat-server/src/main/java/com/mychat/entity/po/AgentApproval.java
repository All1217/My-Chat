package com.mychat.entity.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** Agent 高风险动作审批持久化实体。 */
@Data
@TableName("agent_approval")
public class AgentApproval {

    @TableId(type = IdType.INPUT)
    private String id;
    @TableField("run_id")
    private String runId;
    @TableField("step_id")
    private String stepId;
    @TableField("action_id")
    private String actionId;
    @TableField("idempotency_key")
    private String idempotencyKey;
    private String status;
    @TableField("request_summary")
    private String requestSummary;
    @TableField("decision_note")
    private String decisionNote;
    @TableField("requested_by")
    private String requestedBy;
    @TableField("decided_by")
    private String decidedBy;
    @TableField("requested_at")
    private LocalDateTime requestedAt;
    @TableField("decided_at")
    private LocalDateTime decidedAt;
    @TableField("expires_at")
    private LocalDateTime expiresAt;
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
