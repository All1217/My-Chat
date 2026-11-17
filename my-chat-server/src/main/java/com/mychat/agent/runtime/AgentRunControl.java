package com.mychat.agent.runtime;

/** 运行取消信号端口。 */
public interface AgentRunControl {

    /** 判断 Run 是否已被外部取消。 */
    boolean isCancelled(String runId);

    /** 请求取消 Run。 */
    void cancel(String runId);
}
