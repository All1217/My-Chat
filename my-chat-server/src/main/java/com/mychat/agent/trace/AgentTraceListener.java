package com.mychat.agent.trace;

/** 内部 Trace 事件监听器。 */
@FunctionalInterface
public interface AgentTraceListener {

    /** 接收一个 Span 快照。 */
    void onTrace(AgentTraceEvent event);
}
