package com.mychat.agent.runtime;

import com.mychat.agent.api.AgentRunRequest;
import com.mychat.agent.api.AgentStepResult;
import org.springframework.util.StringUtils;

import java.util.List;

/** 可按场景命名的最终输出策略，优先 finish 正文并回退最后观察。 */
public class ObservationAnswerPolicy implements AnswerPolicy {

    private final String id;

    /** 创建指定 ID 的场景回答策略。 */
    public ObservationAnswerPolicy(String id) {
        this.id = id;
    }

    /** 返回策略 ID。 */
    @Override
    public String id() {
        return id;
    }

    /** 返回 finish 正文或最后一个成功观察。 */
    @Override
    public String answer(
            AgentRunRequest request,
            AgentDecision decision,
            List<AgentStepResult> steps) {
        if (decision != null && StringUtils.hasText(decision.finalOutput())) {
            return decision.finalOutput();
        }
        for (int index = steps.size() - 1; index >= 0; index--) {
            AgentStepResult step = steps.get(index);
            if (step.status() == AgentStepResult.Status.SUCCEEDED
                    && StringUtils.hasText(step.observation())) {
                return step.observation();
            }
        }
        return "";
    }
}
