package com.mychat.vo;

import com.mychat.entity.po.AgentRun;

import java.time.LocalDateTime;

/** 不包含 checkpoint 和隐藏上下文的 Agent Run 查询视图。 */
public record AgentRunSummaryVO(
        String id,
        String scenario,
        String profileVersion,
        String businessRef,
        String conversationId,
        String status,
        int currentStep,
        String inputSummary,
        String outputSummary,
        String errorMessage,
        long totalTokens,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime completedAt) {

    /** 从持久化实体创建安全视图。 */
    public static AgentRunSummaryVO from(AgentRun run) {
        return new AgentRunSummaryVO(
                run.getId(),
                run.getScenario(),
                run.getProfileVersion(),
                run.getBusinessRef(),
                run.getConversationId(),
                run.getStatus(),
                run.getCurrentStep() == null ? 0 : run.getCurrentStep(),
                run.getInputSummary(),
                run.getOutputSummary(),
                run.getErrorMessage(),
                run.getTotalTokens() == null ? 0 : run.getTotalTokens(),
                run.getCreatedAt(),
                run.getUpdatedAt(),
                run.getCompletedAt());
    }
}
