package com.mychat.agent.runtime;

import com.mychat.agent.api.AgentRunRequest;
import com.mychat.agent.api.AgentRunResult;

/** 通用 Agent Runtime 入口。 */
public interface AgentRuntime {

    /** 按请求场景运行 Agent。 */
    AgentRunResult run(AgentRunRequest request);

    /** 从最后一个安全 checkpoint 恢复 Run。 */
    AgentRunResult resume(String runId);

    /** 请求取消 Run。 */
    void cancel(String runId);
}
