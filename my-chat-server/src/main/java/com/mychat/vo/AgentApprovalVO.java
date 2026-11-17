package com.mychat.vo;

import com.mychat.entity.po.AgentApproval;

import java.time.LocalDateTime;

/** Agent 审批公开视图。 */
public record AgentApprovalVO(
        String id,
        String runId,
        String stepId,
        String actionId,
        String status,
        String requestSummary,
        String decisionNote,
        String requestedBy,
        String decidedBy,
        LocalDateTime requestedAt,
        LocalDateTime decidedAt) {

    /** 从审批实体创建视图。 */
    public static AgentApprovalVO from(AgentApproval approval) {
        return new AgentApprovalVO(
                approval.getId(),
                approval.getRunId(),
                approval.getStepId(),
                approval.getActionId(),
                approval.getStatus(),
                approval.getRequestSummary(),
                approval.getDecisionNote(),
                approval.getRequestedBy(),
                approval.getDecidedBy(),
                approval.getRequestedAt(),
                approval.getDecidedAt());
    }
}
