package com.mychat.service.agent.worker;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * general Worker：无工具纯对话，完成编排器交给的子任务。
 */
@Service
public class GeneralWorker {

    private final ChatClient agentWorkflowChatClient;

    public GeneralWorker(@Qualifier("agentWorkflowChatClient") ChatClient agentWorkflowChatClient) {
        this.agentWorkflowChatClient = agentWorkflowChatClient;
    }

    /**
     * 执行一般推理/润色步，不调用外部工具。
     */
    public String run(String userMessage) {
        String content = agentWorkflowChatClient.prompt()
                .system("你是友好的助手，用简洁中文完成编排器交给你的子任务。不要尝试调用外部工具。")
                .user(userMessage)
                .call()
                .content();
        return content != null ? content : "";
    }
}
