package com.mychat.agent.capability;

import com.mychat.agent.api.AgentRunRequest;
import com.mychat.agent.api.AgentScenarioId;
import com.mychat.agent.runtime.ScenarioProfileRegistry;
import com.mychat.agent.tool.ChatClientProfileFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Set;

/** 使用 Profile 与动作工具交集执行受限模型调用。 */
@Component
public class ProfileToolCapability {

    private final ChatClientProfileFactory clientFactory;
    private final ScenarioProfileRegistry profileRegistry;

    /** 注入 Profile 客户端工厂和注册表。 */
    public ProfileToolCapability(
            ChatClientProfileFactory clientFactory,
            ScenarioProfileRegistry profileRegistry) {
        this.clientFactory = clientFactory;
        this.profileRegistry = profileRegistry;
    }

    /** 在独立临时 Memory 中执行受限工具任务。 */
    public String execute(
            AgentScenarioId scenarioId,
            AgentRunRequest request,
            String message,
            Set<String> actionTools) {
        ChatClient.ChatClientRequestSpec prompt = clientFactory.create(
                        profileRegistry.require(scenarioId), actionTools)
                .prompt()
                .user(message)
                .advisors(advisor -> advisor.param(
                        ChatMemory.CONVERSATION_ID,
                        "agent:" + request.context().runId()));
        if (StringUtils.hasText(request.context().workDir())) {
            prompt.toolContext(Map.of("workDir", request.context().workDir()));
        }
        String content = prompt
                .call()
                .content();
        return content == null ? "" : content;
    }
}
