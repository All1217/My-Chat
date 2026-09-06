package com.mychat.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.tool.execution.DefaultToolExecutionExceptionProcessor;
import org.springframework.ai.tool.execution.ToolExecutionExceptionProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 装配会话记忆、工具失败回灌，以及三个按默认模型热切换的 ChatClient。 */
@Configuration
public class AiConfiguration {
    @Autowired
    JdbcChatMemoryRepository jdbcChatMemoryRepository;

    /** JDBC 窗口记忆，供 tool / rag ChatClient 共用。 */
    @Bean
    public ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(jdbcChatMemoryRepository)
                .maxMessages(64)
                .build();
    }

    /**
     * MCP / 工具失败时把错误作为 tool result 回灌模型，而不是中断整次 {@code ChatClient.call()}。
     * <p>
     * 默认 alwaysThrow=true 时，远程 MCP 403/500 会直接打断 Orchestrator search Worker；
     * alwaysThrow=false 后模型可收尾说明「联网暂不可用」，编排循环也能拿到 observation。
     */
    @Bean
    public ToolExecutionExceptionProcessor toolExecutionExceptionProcessor() {
        return new DefaultToolExecutionExceptionProcessor(false);
    }

    /** 普通对话客户端：转发到当前默认模型的 tool 层。 */
    @Bean
    public ChatClient toolChatClient(ChatClientRegistry registry) {
        return new DelegatingChatClient(registry::tool);
    }

    /** 知识库 RAG 客户端：转发到当前默认模型的 rag 层。 */
    @Bean
    public ChatClient ragChatClient(ChatClientRegistry registry) {
        return new DelegatingChatClient(registry::rag);
    }

    /** 编排 / 分类客户端：转发到当前默认模型的 workflow 层。 */
    @Bean
    public ChatClient agentWorkflowChatClient(ChatClientRegistry registry) {
        return new DelegatingChatClient(registry::workflow);
    }
}
