package com.mychat.agent;

import com.mychat.agent.dto.RouteRequest;
import com.mychat.agent.dto.RouteResultVO;
import com.mychat.agent.patterns.RoutingWorkflow;
import com.mychat.config.WorkspaceContext;
import com.mychat.utils.WorkspaceUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * Routing 编排服务：分类（LLM）→ Java switch 分发到专用 ChatClient。
 * <p>
 * <b>代码路径 vs 单次 LLM</b>：
 * <ul>
 *   <li>分类：{@link RoutingWorkflow#determineRoute} 一次 LLM（无工具 Client）。</li>
 *   <li>分发：本类 {@code switch} 固定路径，不是模型自己决定下一步。</li>
 *   <li>处理：各分支再发起一次（或带工具循环的）ChatClient 调用。</li>
 * </ul>
 */
@Slf4j
@Service
public class AgentRoutingService {

    private final RoutingWorkflow routingWorkflow;
    private final ChatClient toolChatClient;
    private final ChatClient ragChatClient;
    private final ChatClient agentWorkflowChatClient;
    private final VectorStore vectorStore;
    private final WorkspaceUtil workspaceUtil;

    public AgentRoutingService(
            @Qualifier("agentWorkflowChatClient") ChatClient agentWorkflowChatClient,
            @Qualifier("toolChatClient") ChatClient toolChatClient,
            @Qualifier("ragChatClient") ChatClient ragChatClient,
            VectorStore vectorStore,
            WorkspaceUtil workspaceUtil) {
        this.agentWorkflowChatClient = agentWorkflowChatClient;
        this.toolChatClient = toolChatClient;
        this.ragChatClient = ragChatClient;
        this.vectorStore = vectorStore;
        this.workspaceUtil = workspaceUtil;
        // 分类器必须用无工具 Client，避免「分类阶段就去 ls/搜网」
        this.routingWorkflow = new RoutingWorkflow(agentWorkflowChatClient);
    }

    /**
     * 校验入参 → 分类 → 按路由调用对应处理器。
     *
     * @throws IllegalArgumentException 参数非法（如 input 为空、kb 路由缺 kbId）
     */
    public RouteResultVO route(RouteRequest request) {
        if (request == null || !StringUtils.hasText(request.getInput())) {
            throw new IllegalArgumentException("input 不能为空");
        }
        String input = request.getInput().trim();

        RoutingWorkflow.RoutingResponse classified = routingWorkflow.determineRoute(input);
        String route = classified.selection();
        String reasoning = classified.reasoning();

        log.info("Routing 分类结果: route={}, reasoning={}", route, reasoning);

        // kb 路由在分发前校验 kbId，避免无意义的 RAG 调用
        if ("kb".equals(route) && !StringUtils.hasText(request.getKbId())) {
            throw new IllegalArgumentException("路由为 kb 时必须提供 kbId");
        }

        String answer = switch (route) {
            case "file" -> handleFile(input);
            case "kb" -> handleKb(input, request.getKbId().trim());
            case "search" -> handleSearch(input);
            default -> handleGeneral(input);
        };

        return new RouteResultVO(route, reasoning, answer);
    }

    /** file：走 toolChatClient（FileTools），使用默认工作区根目录 */
    private String handleFile(String input) {
        String conversationId = "agent-route-file-" + UUID.randomUUID();
        WorkspaceContext.set(workspaceUtil.getWorkspaceRoot().toString());
        try {
            return toolChatClient.prompt()
                    .user(input)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                    .call()
                    .content();
        } finally {
            WorkspaceContext.clear();
        }
    }

    /** kb：ragChatClient + 按 kbId 过滤的 QuestionAnswerAdvisor（同步 call） */
    private String handleKb(String input, String kbId) {
        String conversationId = "agent-route-kb-" + UUID.randomUUID();
        QuestionAnswerAdvisor qaAdvisor = QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(SearchRequest.builder()
                        .topK(5)
                        .similarityThreshold(0.5)
                        .filterExpression("kbId == '" + kbId + "'")
                        .build())
                .build();

        return ragChatClient.prompt()
                .advisors(qaAdvisor)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .user(input)
                .call()
                .content();
    }

    /** search：复用 toolChatClient，强调优先使用搜索 / 天气等 MCP 工具 */
    private String handleSearch(String input) {
        String conversationId = "agent-route-search-" + UUID.randomUUID();
        WorkspaceContext.set(workspaceUtil.getWorkspaceRoot().toString());
        try {
            return toolChatClient.prompt()
                    .system("""
                            当前请求已路由到 search。请优先调用可用的远程 MCP 工具
                            （网页搜索、天气查询等）获取信息后再回答；不要编造实时数据。
                            """)
                    .user(input)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                    .call()
                    .content();
        } finally {
            WorkspaceContext.clear();
        }
    }

    /** general：无工具纯对话 */
    private String handleGeneral(String input) {
        return agentWorkflowChatClient.prompt()
                .system("你是友好的助手，用简洁中文回答用户的一般问题。")
                .user(input)
                .call()
                .content();
    }
}
