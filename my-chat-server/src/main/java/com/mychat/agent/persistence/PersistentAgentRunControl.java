package com.mychat.agent.persistence;

import com.mychat.agent.runtime.AgentRunControl;
import com.mychat.entity.po.AgentRun;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** 通过 agent_run.status 传播跨线程、跨进程取消信号。 */
@Component
public class PersistentAgentRunControl implements AgentRunControl {

    private final AgentRunRepository repository;
    private final com.mychat.mapper.AgentRunMapper mapper;

    /** 注入运行仓库与 Mapper。 */
    public PersistentAgentRunControl(AgentRunRepository repository,
                                     com.mychat.mapper.AgentRunMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    /** 查询持久化取消状态。 */
    @Override
    public boolean isCancelled(String runId) {
        try {
            return "CANCELLED".equals(repository.require(runId).getStatus());
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    /** 将 Run 标记为取消。 */
    @Override
    public void cancel(String runId) {
        repository.require(runId);
        AgentRun update = new AgentRun();
        update.setId(runId);
        update.setStatus("CANCELLED");
        update.setCompletedAt(LocalDateTime.now());
        update.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(update);
    }
}
