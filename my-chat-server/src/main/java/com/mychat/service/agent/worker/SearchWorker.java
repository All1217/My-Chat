package com.mychat.service.agent.worker;

import com.mychat.config.WorkspaceContext;
import com.mychat.utils.SearchSystemPrompts;
import com.mychat.utils.WorkspaceUtil;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * search Worker：toolChatClient（searchWeb + FileTools + YAML MCP）+ 联网搜索 system prompt。
 */
@Service
public class SearchWorker {

    private final ChatClient toolChatClient;
    private final WorkspaceUtil workspaceUtil;

    public SearchWorker(
            @Qualifier("toolChatClient") ChatClient toolChatClient,
            WorkspaceUtil workspaceUtil) {
        this.toolChatClient = toolChatClient;
        this.workspaceUtil = workspaceUtil;
    }

    /**
     * 执行搜索步。内部 set/clear WorkspaceContext，与重构前一致。
     */
    public String run(String userMessage, String workDir) {
        String conversationId = "orch-search-" + UUID.randomUUID();
        String root = StringUtils.hasText(workDir)
                ? workDir
                : workspaceUtil.getWorkspaceRoot().toString();
        WorkspaceContext.set(root);
        try {
            String content = toolChatClient.prompt()
                    .system(SearchSystemPrompts.SEARCH)
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
