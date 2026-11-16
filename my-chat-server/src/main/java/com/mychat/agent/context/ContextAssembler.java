package com.mychat.agent.context;

import com.mychat.agent.api.AgentRunContext;

import java.util.List;

/** 场景无关上下文装配器。 */
public interface ContextAssembler {

    /** 按运行上下文与预算生成不可变 ContextBundle。 */
    ContextBundle assemble(
            AgentRunContext runContext,
            List<ContextBlock> blocks,
            ContextBudgetPolicy policy);
}
