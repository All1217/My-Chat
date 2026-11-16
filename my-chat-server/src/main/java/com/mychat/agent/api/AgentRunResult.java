package com.mychat.agent.api;

import java.util.List;
import java.util.Map;

/** 通用 Agent 运行结果；Phase 1 仅定义契约，不接管生产路径。 */
public record AgentRunResult(
        Status status,
        String output,
        List<AgentStepResult> steps,
        String errorMessage,
        Map<String, Object> metrics) {

    /** 运行终态。 */
    public enum Status {
        SUCCEEDED,
        FAILED,
        CANCELLED,
        PAUSED,
        WAITING_APPROVAL,
        BUDGET_EXHAUSTED
    }

    /** 固化集合，保证结果可安全跨线程传递。 */
    public AgentRunResult {
        output = output == null ? "" : output;
        steps = steps == null ? List.of() : List.copyOf(steps);
        metrics = metrics == null ? Map.of() : Map.copyOf(metrics);
    }
}
