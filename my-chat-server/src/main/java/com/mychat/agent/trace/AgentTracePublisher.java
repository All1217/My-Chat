package com.mychat.agent.trace;

import org.springframework.stereotype.Component;

import java.util.List;

/** 将内部 Trace 分发给持久化和公开进度适配器。 */
@Component
public class AgentTracePublisher {

    private final List<AgentTraceListener> listeners;

    /** 注入所有 Trace Listener。 */
    public AgentTracePublisher(List<AgentTraceListener> listeners) {
        this.listeners = List.copyOf(listeners);
    }

    /** 发布 Trace。 */
    public void publish(AgentTraceEvent event) {
        listeners.forEach(listener -> listener.onTrace(event));
    }
}
