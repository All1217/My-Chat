package com.mychat.agent.action;

import com.mychat.agent.api.AgentAction;
import com.mychat.agent.api.AgentRunRequest;
import com.mychat.agent.api.AgentStepResult;
import com.mychat.agent.runtime.AgentActionHandler;
import com.mychat.agent.runtime.AgentDecision;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Map;

/** 无工具的一般分析与生成 Action。 */
@Component
public class GeneralActionHandler implements AgentActionHandler {

    private final ChatClient chatClient;

    /** 注入无工具工作流客户端。 */
    public GeneralActionHandler(
            @Qualifier("agentWorkflowChatClient") ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /** 返回动作描述。 */
    @Override
    public AgentAction action() {
        return new AgentAction("general", "无工具的一般推理与写作", "{}", "text",
                AgentAction.SideEffectLevel.READ_ONLY);
    }

    /** 执行一般推理。 */
    @Override
    public AgentStepResult handle(AgentRunRequest request, AgentDecision decision, int stepIndex) {
        String message = ActionPromptSupport.message(request, decision);
        String observation = chatClient.prompt()
                .system("你是无工具分析能力。只基于显式上下文完成当前任务，不编造外部事实。")
                .user(message)
                .call()
                .content();
        return new AgentStepResult(stepIndex, action().id(), AgentStepResult.Status.SUCCEEDED,
                observation == null ? "" : observation, null, Map.of("toolCalls", 0));
    }
}
