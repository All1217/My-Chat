package com.mychat.agent.runtime;

import com.mychat.agent.api.AgentAction;
import com.mychat.agent.api.AgentRunRequest;
import com.mychat.agent.api.AgentStepResult;

/** 写动作执行前后的幂等与审批持久化端口。 */
public interface ActionExecutionJournal {

    /** 执行前占用幂等键，或返回已完成结果/不确定状态。 */
    Preparation prepare(AgentRunRequest request, AgentDecision decision,
                        AgentAction action, int stepIndex);

    /** 创建审批记录。 */
    void requestApproval(AgentRunRequest request, AgentDecision decision,
                         AgentAction action, int stepIndex, String idempotencyKey);

    /** 判断动作是否已获批准。 */
    boolean isApproved(AgentRunRequest request, int stepIndex, String actionId);

    /** 幂等准备结果。 */
    record Preparation(String idempotencyKey, AgentStepResult replayResult, String blockedReason) {

        /** 是否可立即执行。 */
        public boolean proceed() {
            return replayResult == null && (blockedReason == null || blockedReason.isBlank());
        }
    }
}
