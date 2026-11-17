package com.mychat.agent.persistence;

import com.mychat.agent.api.AgentRunRequest;
import com.mychat.agent.api.AgentStepResult;

import java.util.List;

/** 从持久化 checkpoint 恢复出的运行输入和已完成步骤。 */
public record AgentCheckpoint(AgentRunRequest request, List<AgentStepResult> completedSteps) {

    /** 固化步骤列表。 */
    public AgentCheckpoint {
        completedSteps = completedSteps == null ? List.of() : List.copyOf(completedSteps);
    }
}
