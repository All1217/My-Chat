package com.mychat.agent.runtime;

import com.mychat.agent.api.AgentRunRequest;
import com.mychat.agent.api.AgentStepResult;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/** MAIN_CHAT 最终答复策略：直接使用 Planner finish 正文，不重复检索。 */
@Component
public class MainChatAnswerPolicy implements AnswerPolicy {

    /** 策略 ID。 */
    @Override
    public String id() {
        return "main-chat";
    }

    /** 返回最终正文；缺失时回退最后一个成功观察。 */
    @Override
    public String answer(AgentRunRequest request, AgentDecision decision,
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
        return "任务未产生可用结果。";
    }
}
