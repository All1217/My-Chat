package com.mychat.mcp;

import org.springframework.ai.chat.client.ChatClient;

import java.util.Set;

/**
 * MCP 工具挂载桥：主干只依赖此接口，不感知 McpSyncClient。
 */
public interface McpToolBridge {

    /**
     * 将当前可用的 MCP ToolCallback 挂到 ChatClient.Builder；无可用连接时为 no-op。
     */
    void attachTo(ChatClient.Builder builder);

    /**
     * 仅挂载指定连接名的 MCP 工具；connectionNames 为空时等同 {@link #attachTo}。
     */
    default void attachTo(ChatClient.Builder builder, Set<String> connectionNames) {
        attachTo(builder);
    }
}
