package com.mychat.agent.api;

/** 可注册 Agent 动作的场景无关描述。 */
public record AgentAction(
        String id,
        String description,
        String inputSchema,
        String outputType,
        SideEffectLevel sideEffectLevel) {

    /** 动作副作用等级，用于后续 Tool Policy 和审批。 */
    public enum SideEffectLevel {
        READ_ONLY,
        REVERSIBLE_WRITE,
        HIGH_RISK_WRITE
    }

    /** 校验动作最小契约。 */
    public AgentAction {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("AgentAction.id 不能为空");
        }
        description = description == null ? "" : description;
        inputSchema = inputSchema == null ? "{}" : inputSchema;
        outputType = outputType == null ? "text" : outputType;
        sideEffectLevel = sideEffectLevel == null ? SideEffectLevel.READ_ONLY : sideEffectLevel;
    }
}
