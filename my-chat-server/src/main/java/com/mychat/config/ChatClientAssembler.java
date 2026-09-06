package com.mychat.config;

import com.mychat.tools.FileTools;
import com.mychat.tools.WebSearchTools;
import io.modelcontextprotocol.client.McpSyncClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.mcp.McpToolNamePrefixGenerator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;

/**
 * 按能力层给 ChatModel 挂 system / 工具 / 记忆，与原先 AiConfiguration 三个 Bean 一致。
 */
@Component
public class ChatClientAssembler {

    private final ChatMemory chatMemory;
    private final FileTools fileTools;
    private final WebSearchTools webSearchTools;
    private final ObjectProvider<List<McpSyncClient>> mcpSyncClients;
    private final ObjectProvider<McpToolNamePrefixGenerator> prefixGenerator;

    /** 注入记忆与工具，装配时按角色取用。 */
    public ChatClientAssembler(ChatMemory chatMemory,
                               FileTools fileTools,
                               WebSearchTools webSearchTools,
                               ObjectProvider<List<McpSyncClient>> mcpSyncClients,
                               ObjectProvider<McpToolNamePrefixGenerator> prefixGenerator) {
        this.chatMemory = chatMemory;
        this.fileTools = fileTools;
        this.webSearchTools = webSearchTools;
        this.mcpSyncClients = mcpSyncClients;
        this.prefixGenerator = prefixGenerator;
    }

    /** 普通对话：FileTools + searchWeb + MCP。 */
    public ChatClient tool(ChatModel model) {
        ChatClient.Builder builder = ChatClient.builder(model)
                .defaultSystem("""
                        涉及文件的查看、创建、写入、修改、删除、重命名、复制操作，请积极调用 FileTools 执行。
                        网页搜索优先调用 searchWeb（本机直连 Exa REST）。
                        天气等其它外部能力，调用当前已挂载的 MCP 工具。
                        """)
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(),
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                .defaultTools(fileTools, webSearchTools);

        List<McpSyncClient> clients = mcpSyncClients.getIfAvailable();
        if (!CollectionUtils.isEmpty(clients)) {
            builder.defaultToolCallbacks(new TolerantMcpToolCallbackProvider(
                    clients, prefixGenerator.getIfAvailable()));
        }
        return builder.build();
    }

    /** 知识库 RAG：无工具，强制基于检索上下文。 */
    public ChatClient rag(ChatModel model) {
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

    /** 编排 / 分类 / 测通：无工具、无会话记忆。 */
    public ChatClient workflow(ChatModel model) {
        return ChatClient.builder(model)
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
    }
}
