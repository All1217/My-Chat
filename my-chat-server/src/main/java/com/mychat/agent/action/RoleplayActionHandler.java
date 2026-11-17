package com.mychat.agent.action;

import com.mychat.agent.api.AgentAction;
import com.mychat.agent.api.AgentRunRequest;
import com.mychat.agent.api.AgentStepResult;
import com.mychat.agent.runtime.AgentActionHandler;
import com.mychat.agent.runtime.AgentDecision;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

/** ROLEPLAY 的无工具角色对话动作，使用独立会话 Memory。 */
@Component
public class RoleplayActionHandler implements AgentActionHandler {

    private final ChatClient chatClient;

    /** 注入无工具工作流客户端。 */
    public RoleplayActionHandler(
            @Qualifier("agentWorkflowChatClient") ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /** 返回无副作用角色扮演动作。 */
    @Override
    public AgentAction action() {
        return new AgentAction(
                "roleplay",
                "按指定角色设定进行对话",
                "{\"message\":\"string\"}",
                "text",
                AgentAction.SideEffectLevel.READ_ONLY);
    }

    /** 使用角色 system 和隔离 conversationId 生成回答。 */
    @Override
    public AgentStepResult handle(
            AgentRunRequest request, AgentDecision decision, int stepIndex) {
        String roleSystem = String.valueOf(request.parameters().getOrDefault(
                "roleSystem", "你正在进行角色扮演。始终保持角色设定，不调用任何工具。"));
        String message = StringUtils.hasText(decision.instruction())
                ? decision.instruction() : request.input();
        String conversationId = "roleplay:" + request.context().conversationId();
        String content = chatClient.prompt()
                .system(roleSystem)
                .user(message)
                .advisors(advisor -> advisor.param(
                        ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();
        return new AgentStepResult(
                stepIndex, action().id(), AgentStepResult.Status.SUCCEEDED,
                content == null ? "" : content, null,
                Map.of("toolCalls", 0, "memoryScope", "isolated"));
    }
}
