package com.mychat.config;

import com.mychat.apps.papertrade.tools.PaperDecisionTools;
import com.mychat.apps.papertrade.tools.PaperResearchNoteTools;
import com.mychat.apps.papertrade.tools.PaperResearchRunTools;
import com.mychat.apps.papertrade.tools.PaperStageSummaryTools;
import com.mychat.mcp.McpToolBridge;
import com.mychat.tools.FileTools;
import com.mychat.tools.WebSearchTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 按能力层给 ChatModel 挂 system / 工具 / 记忆，与原先 AiConfiguration 三个 Bean 一致。
 */
@Component
public class ChatClientAssembler {

    private final ChatMemory chatMemory;
    private final FileTools fileTools;
    private final WebSearchTools webSearchTools;
    private final PaperStageSummaryTools stageSummaryTools;
    private final PaperDecisionTools decisionTools;
    private final PaperResearchRunTools researchRunTools;
    private final PaperResearchNoteTools researchNoteTools;
    private final ObjectProvider<McpToolBridge> mcpToolBridge;

    /** 注入记忆与工具，装配时按角色取用；MCP 经可选桥接入。 */
    public ChatClientAssembler(ChatMemory chatMemory,
                               FileTools fileTools,
                               WebSearchTools webSearchTools,
                               PaperStageSummaryTools stageSummaryTools,
                               PaperDecisionTools decisionTools,
                               PaperResearchRunTools researchRunTools,
                               PaperResearchNoteTools researchNoteTools,
                               ObjectProvider<McpToolBridge> mcpToolBridge) {
        this.chatMemory = chatMemory;
        this.fileTools = fileTools;
        this.webSearchTools = webSearchTools;
        this.stageSummaryTools = stageSummaryTools;
        this.decisionTools = decisionTools;
        this.researchRunTools = researchRunTools;
        this.researchNoteTools = researchNoteTools;
        this.mcpToolBridge = mcpToolBridge;
    }

    /** 普通对话：文件、网页搜索、模拟炒股记录，以及可选 MCP。 */
    public ChatClient tool(ChatModel model) {
        ChatClient.Builder builder = ChatClient.builder(model)
                .defaultSystem("""
                        涉及文件的查看、创建、写入、修改、删除、重命名、复制操作，请积极调用 FileTools 执行。
                        网页搜索优先调用 searchWeb（本机直连 Exa REST）。
                        模拟炒股的阶段总结、AI 决策、搜集轮次、研究卡片，调用已挂载的 papertrade 工具，不要编造记录。
                        天气等其它外部能力，调用当前已挂载的 MCP 工具。
                        """)
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(),
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                .defaultTools(fileTools, webSearchTools, stageSummaryTools, decisionTools,
                        researchRunTools, researchNoteTools);

        McpToolBridge bridge = mcpToolBridge.getIfAvailable();
        if (bridge != null) {
            bridge.attachTo(builder);
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
