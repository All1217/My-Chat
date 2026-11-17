package com.mychat.agent.action;

import com.mychat.agent.api.AgentRunRequest;
import com.mychat.agent.runtime.AgentDecision;
import org.springframework.util.StringUtils;

/** Action Worker 的统一 ContextBundle 提示拼装。 */
final class ActionPromptSupport {

    private ActionPromptSupport() {
    }

    /** 使用唯一 contextText，并追加当前动作任务。 */
    static String message(AgentRunRequest request, AgentDecision decision) {
        Object contextText = request.parameters().get("contextText");
        if (contextText != null && StringUtils.hasText(String.valueOf(contextText))) {
            return """
                    【本次运行上下文】
                    %s

                    【当前动作任务】
                    %s
                    """.formatted(contextText, decision.instruction());
        }
        return StringUtils.hasText(decision.instruction())
                ? decision.instruction() : request.input();
    }
}
