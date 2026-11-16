package com.mychat.agent.api;

import java.util.Map;

/** 面向用户的公开进度契约；不承载模型 reasoning 或隐藏提示词。 */
public record PublicProgressEvent(
        String runId,
        String stepId,
        String parentStepId,
        Type type,
        AgentStepResult.Status status,
        String displayName,
        String publicSummary,
        long elapsedMs,
        Map<String, Object> details) {

    /** 公开进度类型。 */
    public enum Type {
        ROUTE,
        STEP,
        TOOL,
        GUARDRAIL,
        QUALITY,
        ERROR,
        DONE
    }

    /** 固化公开字段，确保事件可安全归约。 */
    public PublicProgressEvent {
        displayName = displayName == null ? "" : displayName;
        publicSummary = publicSummary == null ? "" : publicSummary;
        details = details == null ? Map.of() : Map.copyOf(details);
    }
}
