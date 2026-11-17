package com.mychat.agent.runtime;

import com.mychat.agent.api.AgentRunRequest;
import com.mychat.agent.api.AgentStepResult;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;

/**
 * 模拟炒股批处理回答策略：优先动作观察（ResearchCardBatch / TradeDecisionBatch），
 * 禁止依赖 Planner finalOutput 承载整批 JSON。
 */
public class PaperBatchAnswerPolicy implements AnswerPolicy {

    private final String id;
    private final Set<String> preferredActions;

    /** 指定策略 ID 与优先动作。 */
    public PaperBatchAnswerPolicy(String id, Set<String> preferredActions) {
        this.id = id;
        this.preferredActions = preferredActions == null ? Set.of() : Set.copyOf(preferredActions);
    }

    /** 返回策略 ID。 */
    @Override
    public String id() {
        return id;
    }

    /** 先取优先动作观察，再回退任意成功观察；忽略过长 finalOutput。 */
    @Override
    public String answer(
            AgentRunRequest request,
            AgentDecision decision,
            List<AgentStepResult> steps) {
        for (int index = steps.size() - 1; index >= 0; index--) {
            AgentStepResult step = steps.get(index);
            if (step.status() == AgentStepResult.Status.SUCCEEDED
                    && preferredActions.contains(step.actionId())
                    && StringUtils.hasText(step.observation())) {
                return step.observation();
            }
        }
        for (int index = steps.size() - 1; index >= 0; index--) {
            AgentStepResult step = steps.get(index);
            if (step.status() == AgentStepResult.Status.SUCCEEDED
                    && StringUtils.hasText(step.observation())) {
                return step.observation();
            }
        }
        if (decision != null && StringUtils.hasText(decision.finalOutput())
                && decision.finalOutput().length() <= 200) {
            return decision.finalOutput();
        }
        return "";
    }
}
