package com.mychat.agent.api;

import java.util.Map;

/** 通用 Agent 启动请求；业务输入与运行上下文明确分离。 */
public record AgentRunRequest(
        String input,
        AgentRunContext context,
        Map<String, Object> parameters) {

    /** 固化调用参数，避免异步运行期间被外部修改。 */
    public AgentRunRequest {
        input = input == null ? "" : input;
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        if (context == null) {
            throw new IllegalArgumentException("AgentRunContext 不能为空");
        }
    }
}
