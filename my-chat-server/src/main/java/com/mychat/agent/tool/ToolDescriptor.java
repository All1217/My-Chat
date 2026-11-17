package com.mychat.agent.tool;

import com.mychat.agent.api.AgentAction;

/** Profile 可见的工具组描述。 */
public record ToolDescriptor(
        String id,
        String category,
        AgentAction.SideEffectLevel sideEffectLevel,
        Object toolObject) {

    /** 校验工具最小契约。 */
    public ToolDescriptor {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("ToolDescriptor.id 不能为空");
        }
        category = category == null ? "general" : category;
        sideEffectLevel = sideEffectLevel == null
                ? AgentAction.SideEffectLevel.READ_ONLY : sideEffectLevel;
    }
}
