package com.mychat.agent.persistence;

import com.mychat.agent.trace.AgentTraceEvent;
import com.mychat.agent.trace.AgentTraceListener;
import org.springframework.stereotype.Component;

/** 将内部 Trace Span 写入 agent_run_step。 */
@Component
public class PersistentAgentTraceListener implements AgentTraceListener {

    private final AgentRunRepository repository;

    /** 注入运行仓库。 */
    public PersistentAgentTraceListener(AgentRunRepository repository) {
        this.repository = repository;
    }

    /** 保存 Trace 快照。 */
    @Override
    public void onTrace(AgentTraceEvent event) {
        repository.saveTrace(event);
    }
}
