package com.mychat.agent.trace;

import com.mychat.agent.api.AgentStepResult;

import java.util.List;
import java.util.Map;

/** 内部 Agent Trace Span 事件。 */
public record AgentTraceEvent(
        String runId,
        String stepId,
        String parentStepId,
        Type type,
        AgentStepResult.Status status,
        String name,
        String inputSummary,
        String outputSummary,
        List<Object> citations,
        long elapsedMs,
        String errorMessage,
        Map<String, Object> metadata) {

    /** Trace Span 类型。 */
    public enum Type {
        WORKFLOW,
        ACTION,
        MODEL,
        TOOL,
        GUARDRAIL,
        HANDOFF,
        EVALUATOR,
        APPROVAL
    }

    /** 固化公开集合并清理空文本。 */
    public AgentTraceEvent {
        name = name == null ? "" : name;
        inputSummary = inputSummary == null ? "" : inputSummary;
        outputSummary = outputSummary == null ? "" : outputSummary;
        citations = citations == null ? List.of() : List.copyOf(citations);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
