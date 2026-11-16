package com.mychat.agent.api;

import java.util.Map;

/** 单次 Agent 运行的显式上下文，取代运行时对 ThreadLocal 的依赖。 */
public record AgentRunContext(
        String runId,
        AgentScenarioId scenarioId,
        String conversationId,
        String userId,
        String kbId,
        String workDir,
        String businessRef,
        Map<String, String> metadata) {

    /** 防止调用方在运行中修改上下文元数据。 */
    public AgentRunContext {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    /** 建立主聊天 shadow 上下文；Phase 1 不启动真实 Agent Run。 */
    public static AgentRunContext mainChatShadow(
            String runId, String conversationId, String kbId, String workDir) {
        return new AgentRunContext(
                runId,
                AgentScenarioId.MAIN_CHAT,
                conversationId,
                null,
                kbId,
                workDir,
                null,
                Map.of("mode", "shadow"));
    }
}
