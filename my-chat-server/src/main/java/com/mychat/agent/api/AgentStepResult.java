package com.mychat.agent.api;

import java.util.Map;

/** Agent 单步执行结果，供运行时、Trace 与公开进度适配复用。 */
public record AgentStepResult(
        int index,
        String actionId,
        Status status,
        String observation,
        String errorMessage,
        Map<String, Object> metadata) {

    /** 单步状态。 */
    public enum Status {
        PENDING,
        RUNNING,
        SUCCEEDED,
        FAILED,
        SKIPPED
    }

    /** 固化元数据并清理空文本。 */
    public AgentStepResult {
        actionId = actionId == null ? "" : actionId;
        status = status == null ? Status.PENDING : status;
        observation = observation == null ? "" : observation;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
