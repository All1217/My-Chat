package com.mychat.mcp;

import org.springframework.ai.chat.client.ChatClient;

/**
 * MCP 工具挂载桥：主干只依赖此接口，不感知 McpSyncClient。
 */
public interface McpToolBridge {

    /**
     * 将当前可用的 MCP ToolCallback 挂到 ChatClient.Builder；无可用连接时为 no-op。
     */
    void attachTo(ChatClient.Builder builder);
}
