package com.mychat.agent.trace;

import org.springframework.stereotype.Component;

/** 把工具、审批、guardrail 与质量 Span 推入在线公开时间线。 */
@Component
public class PublicAgentTraceListener implements AgentTraceListener {

    private final PublicProgressAdapter adapter;
    private final AgentProgressHub hub;

    /** 注入公开适配器和事件路由器。 */
    public PublicAgentTraceListener(PublicProgressAdapter adapter, AgentProgressHub hub) {
        this.adapter = adapter;
        this.hub = hub;
    }

    /** 模型 Span 仅内部持久化，其余安全 Span 可公开。 */
    @Override
    public void onTrace(AgentTraceEvent event) {
        if (event.type() != AgentTraceEvent.Type.MODEL
                && event.type() != AgentTraceEvent.Type.WORKFLOW) {
            hub.publish(adapter.adapt(event));
        }
    }
}
