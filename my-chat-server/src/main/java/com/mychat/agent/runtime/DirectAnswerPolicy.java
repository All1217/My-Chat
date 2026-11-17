package com.mychat.agent.runtime;

import com.mychat.agent.api.AgentRunRequest;
import com.mychat.agent.api.AgentStepResult;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/** 优先返回决策器终稿，否则返回最后一步观察。 */
@Component
public class DirectAnswerPolicy implements AnswerPolicy {

    /** 策略名。 */
    @Override
    public String id() {
        return "direct";
    }

    /** 解析直接答复。 */
    @Override
    public String answer(AgentRunRequest request, AgentDecision decision, List<AgentStepResult> steps) {
        if (decision != null && StringUtils.hasText(decision.finalOutput())) {
            return decision.finalOutput();
        }
        if (steps != null && !steps.isEmpty()) {
            return steps.get(steps.size() - 1).observation();
        }
        return "";
    }
}
