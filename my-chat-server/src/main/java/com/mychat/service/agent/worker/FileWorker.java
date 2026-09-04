package com.mychat.service.agent.worker;

import com.mychat.config.WorkspaceContext;
import com.mychat.utils.WorkspacePromptBuilder;
import com.mychat.utils.WorkspaceUtil;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * file Worker：toolChatClient（FileTools + YAML MCP）+ 工作区 system prompt。
 */
@Service
public class FileWorker {

    private final ChatClient toolChatClient;
    private final WorkspaceUtil workspaceUtil;
    private final WorkspacePromptBuilder workspacePromptBuilder;

    public FileWorker(
            @Qualifier("toolChatClient") ChatClient toolChatClient,
            WorkspaceUtil workspaceUtil,
            WorkspacePromptBuilder workspacePromptBuilder) {
        this.toolChatClient = toolChatClient;
        this.workspaceUtil = workspaceUtil;
        this.workspacePromptBuilder = workspacePromptBuilder;
    }

    /**
     * 执行文件步。内部 set/clear WorkspaceContext，与重构前一致。
     */
    public String run(String userMessage, String workDir) {
        String conversationId = "orch-file-" + UUID.randomUUID();
        String root = StringUtils.hasText(workDir)
                ? workDir
                : workspaceUtil.getWorkspaceRoot().toString();
        WorkspaceContext.set(root);
        try {
            String content = toolChatClient.prompt()
                    .system(workspacePromptBuilder.build(root))
                    .user(userMessage)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                    .call()
                    .content();
            return content != null ? content : "";
        } finally {
            WorkspaceContext.clear();
        }
    }
}
