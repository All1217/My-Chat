package com.mychat.entity.dto;

import java.util.Map;

/** Phase 2 Runtime 只读 Demo 请求。 */
public record AgentRuntimeDemoRequest(
        String input,
        String conversationId,
        String kbId,
        String workDir,
        Map<String, String> metadata) {

    /** 固化可选元数据。 */
    public AgentRuntimeDemoRequest {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
