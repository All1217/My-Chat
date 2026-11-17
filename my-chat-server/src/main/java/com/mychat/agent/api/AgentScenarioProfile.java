package com.mychat.agent.api;

import java.util.Set;

/** 场景 Profile 契约：声明动作、工具、预算和输出策略。 */
public record AgentScenarioProfile(
        AgentScenarioId scenarioId,
        String version,
        Set<String> allowedActions,
        Set<String> allowedTools,
        int maxSteps,
        int maxToolCalls,
        String outputType,
        String answerPolicy,
        String workflowStrategy,
        String memoryPolicy,
        String contextPolicy,
        String qualityPolicy) {

    /** 兼容早期 Profile 构造方式。 */
    public AgentScenarioProfile(
            AgentScenarioId scenarioId,
            String version,
            Set<String> allowedActions,
            Set<String> allowedTools,
            int maxSteps,
            int maxToolCalls,
            String outputType,
            String answerPolicy) {
        this(scenarioId, version, allowedActions, allowedTools, maxSteps, maxToolCalls,
                outputType, answerPolicy, "orchestrator-workers", "ephemeral",
                "default", "none");
    }

    /** 校验 Profile 并固化白名单。 */
    public AgentScenarioProfile {
        if (scenarioId == null) {
            throw new IllegalArgumentException("AgentScenarioProfile.scenarioId 不能为空");
        }
        version = version == null || version.isBlank() ? "v1" : version;
        allowedActions = allowedActions == null ? Set.of() : Set.copyOf(allowedActions);
        allowedTools = allowedTools == null ? Set.of() : Set.copyOf(allowedTools);
        if (maxSteps < 1 || maxToolCalls < 0) {
            throw new IllegalArgumentException("Agent 场景预算非法");
        }
        outputType = outputType == null ? "text" : outputType;
        answerPolicy = answerPolicy == null ? "direct" : answerPolicy;
        workflowStrategy = normalized(workflowStrategy, "orchestrator-workers");
        memoryPolicy = normalized(memoryPolicy, "ephemeral");
        contextPolicy = normalized(contextPolicy, "default");
        qualityPolicy = normalized(qualityPolicy, "none");
    }

    /** 规范化可选策略 ID。 */
    private static String normalized(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
