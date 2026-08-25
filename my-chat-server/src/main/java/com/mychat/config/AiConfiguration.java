package com.mychat.config;

import com.mychat.tools.FileTools;
import com.mychat.tools.WebSearchTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.tool.execution.DefaultToolExecutionExceptionProcessor;
import org.springframework.ai.tool.execution.ToolExecutionExceptionProcessor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfiguration {
    @Autowired
    JdbcChatMemoryRepository jdbcChatMemoryRepository;

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
     * 默认 alwaysThrow=true 时，Exa Cloudflare 403 等会直接打断 Orchestrator search Worker；
     * alwaysThrow=false 后模型可收尾说明「联网暂不可用」，编排循环也能拿到 observation。
     */
    @Bean
    public ToolExecutionExceptionProcessor toolExecutionExceptionProcessor() {
        return new DefaultToolExecutionExceptionProcessor(false);
    }

    /**
     * 普通对话：本地 FileTools + WebSearchTools + 远程 MCP。
     * <p>
     * Spring AI 2.0：MCP 工具不会自动挂到 ChatClient，必须显式注入
     * {@link SyncMcpToolCallbackProvider} 并调用 {@code defaultTools(...)}。
     * {@link WebSearchTools#searchWeb} 绕开 Smithery→mcp.exa.ai 的 Cloudflare 403。
     */
    @Bean
    public ChatClient toolChatClient(OpenAiChatModel model,
                                     ChatMemory chatMemory,
                                     FileTools fileTools,
                                     WebSearchTools webSearchTools,
                                     ObjectProvider<SyncMcpToolCallbackProvider> mcpTools) {
        ChatClient.Builder builder = ChatClient.builder(model)
                .defaultSystem("""
                        涉及文件的查看、创建、写入、修改、删除、重命名、复制操作，请积极调用可用工具执行。
                        需要联网搜索时优先调用 searchWeb；天气等再按需使用远程 MCP。
                        """)
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(),
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                .defaultTools(fileTools, webSearchTools);

        // 有 SyncMcpToolCallbackProvider 时合并远程 MCP 工具（append，不覆盖本地工具）
        mcpTools.ifAvailable(builder::defaultTools);

        return builder.build();
    }

    /** 知识库 RAG：不注册任何工具，强制基于检索上下文回答 */
    @Bean
    public ChatClient ragChatClient(OpenAiChatModel model, ChatMemory chatMemory) {
        return ChatClient.builder(model)
                .defaultSystem("""
                        你是知识库问答助手。请严格基于系统提供的检索上下文回答问题。
                        若上下文含「文档目录」，请根据目录概括库里有哪些资料，并请用户换一个具体问题；不要编造未列出的文档。
                        若上下文只有若干检索片段，这些片段不是知识库的全部内容，禁止说「仅覆盖这些章节」或把它们当成全集。
                        仅当上下文明确为空且没有文档目录时，才告知用户「知识库中未找到相关信息」，不要编造。
                        不要尝试调用任何外部工具或命令。
                        """)
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(),
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                .build();
    }

    /**
     * Agent Workflow 专用客户端：无 FileTools / MCP、无会话记忆。
     * <p>
     * 用于 Routing 分类器与 general 路由，避免分类阶段误调工具。
     * 与 {@link #toolChatClient}、{@link #ragChatClient} 并列，职责隔离。
     */
    @Bean
    public ChatClient agentWorkflowChatClient(OpenAiChatModel model) {
        return ChatClient.builder(model)
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
    }
}
