package com.mychat.entity.dto;

/** Agent 审批决定请求。 */
public record AgentApprovalDecisionRequest(
        boolean approved,
        String decidedBy,
        String note) {
}
