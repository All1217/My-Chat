package com.mychat.service.agent;

import com.mychat.entity.dto.RouteRequest;
import com.mychat.entity.po.KnowledgeBase;
import com.mychat.entity.po.KnowledgeBaseSettings;
import com.mychat.mapper.KnowledgeBaseMapper;
import com.mychat.service.knowledge.KbSearchRequests;
import com.mychat.service.knowledge.KnowledgeRetrievalService;
import com.mychat.vo.RouteResultVO;
import com.mychat.common.RoutingWorkflow;
import com.mychat.config.WorkspaceContext;
import com.mychat.utils.WorkspaceUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * 单次 Routing：LLM 分类一次后 Java switch 分发。仅 Demo 使用，主聊天不走本类。
 * <p>
 * 产品入口见 {@code ChatController}（Orchestrator）。
 * Demo 流式入口见 {@code POST /ai/agent/route}（{@link AgentRouteDemoStreamService}）；
 * 同步 {@link #route} 仍可供内部/单测调用。
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
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeRetrievalService knowledgeRetrievalService;

    public AgentRoutingService(
            @Qualifier("agentWorkflowChatClient") ChatClient agentWorkflowChatClient,
            @Qualifier("toolChatClient") ChatClient toolChatClient,
            @Qualifier("ragChatClient") ChatClient ragChatClient,
            VectorStore vectorStore,
            WorkspaceUtil workspaceUtil,
            KnowledgeBaseMapper knowledgeBaseMapper,
            KnowledgeRetrievalService knowledgeRetrievalService) {
        this.agentWorkflowChatClient = agentWorkflowChatClient;
        this.toolChatClient = toolChatClient;
        this.ragChatClient = ragChatClient;
        this.vectorStore = vectorStore;
        this.workspaceUtil = workspaceUtil;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.knowledgeRetrievalService = knowledgeRetrievalService;
        // 分类器必须用无工具 Client，避免「分类阶段就去 ls/搜网」
        this.routingWorkflow = new RoutingWorkflow(agentWorkflowChatClient);
    }

    /**
     * Demo 分类：带会话约束（无 kbId 时 kb 回退 general）。主聊天不调用。
     */
    public RoutingWorkflow.RoutingResponse classify(String input, String kbId, String workDir) {
        if (!StringUtils.hasText(input)) {
            throw new IllegalArgumentException("input 不能为空");
        }
        String trimmed = input.trim();
        RoutingWorkflow.RouteContext ctx = new RoutingWorkflow.RouteContext(kbId, workDir);
        RoutingWorkflow.RoutingResponse classified = routingWorkflow.determineRoute(trimmed, ctx);
        return applyConstraints(classified, ctx);
    }

    /**
     * 校验入参 → 分类 → 按路由同步调用对应处理器。
     * <p>
     * Demo {@code POST /ai/agent/route} 已改为 NDJSON（见 {@link AgentRouteDemoStreamService}）；
     * 本方法仍可供内部/单测同步调用。
     *
     * @throws IllegalArgumentException 参数非法（如 input 为空）
     */
    public RouteResultVO route(RouteRequest request) {
        if (request == null || !StringUtils.hasText(request.getInput())) {
            throw new IllegalArgumentException("input 不能为空");
        }
        String input = request.getInput().trim();
        String kbId = request.getKbId();
        String workDir = request.getWorkDir();

        RoutingWorkflow.RoutingResponse classified = classify(input, kbId, workDir);
        String route = classified.selection();
        String reasoning = classified.reasoning();

        log.info("Routing 分类结果: route={}, reasoning={}", route, reasoning);

        if ("kb".equals(route) && !StringUtils.hasText(kbId)) {
            // classify 已约束；防御性兜底
            throw new IllegalArgumentException("路由为 kb 时必须提供 kbId");
        }

        String answer = switch (route) {
            case "file" -> handleFile(input, workDir);
            case "kb" -> handleKb(input, kbId.trim());
            case "search" -> handleSearch(input, workDir);
            default -> handleGeneral(input);
        };

        return new RouteResultVO(route, reasoning, answer);
    }

    /**
     * 构建按 kbId 过滤的 {@link QuestionAnswerAdvisor}；topK / 阈值取自该知识库。
     */
    public QuestionAnswerAdvisor buildKbAdvisor(String kbId) {
        int topK = KnowledgeBaseSettings.DEFAULT_TOP_K;
        double threshold = KnowledgeBaseSettings.DEFAULT_SIMILARITY_THRESHOLD;
        if (StringUtils.hasText(kbId)) {
            KnowledgeBase kb = knowledgeBaseMapper.selectById(kbId.trim());
            if (kb != null) {
                topK = KnowledgeBaseSettings.topKOrDefault(kb.getTopK());
                threshold = KnowledgeBaseSettings.thresholdOrDefault(kb.getSimilarityThreshold());
            }
        }
        return QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(KbSearchRequests.filtered(kbId, topK, threshold).build())
                .build();
    }

    /**
     * 无 kbId 时禁止 kb；其余标签原样返回。
     */
    private RoutingWorkflow.RoutingResponse applyConstraints(
            RoutingWorkflow.RoutingResponse classified,
            RoutingWorkflow.RouteContext ctx) {
        String selection = classified.selection();
        String reasoning = classified.reasoning() != null ? classified.reasoning() : "";

        if ("kb".equals(selection) && !ctx.hasKb()) {
            reasoning = (reasoning.isBlank() ? "" : reasoning + " ")
                    + "[会话未绑定知识库，已从 kb 回退为 general]";
            selection = "general";
            log.info("Routing 约束: kb→general（无 kbId）");
        }

        return new RoutingWorkflow.RoutingResponse(reasoning, selection);
    }

    /**
     * file：走 toolChatClient（FileTools），优先会话 workDir，否则默认工作区
     */
    private String handleFile(String input, String workDir) {
        String conversationId = "agent-route-file-" + UUID.randomUUID();
        String root = StringUtils.hasText(workDir)
                ? workDir.trim()
                : workspaceUtil.getWorkspaceRoot().toString();
        WorkspaceContext.set(root);
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

    /**
     * kb：先按用户原问做向量检索（默认 kbScope=vector），再交给 ragChatClient 生成。
     */
    private String handleKb(String input, String kbId) {
        String conversationId = "agent-route-kb-" + UUID.randomUUID();
        KnowledgeRetrievalService.RagContext rag = knowledgeRetrievalService.buildRagContext(kbId, input);
        String userMessage = KnowledgeRetrievalService.wrapUserWithContext(input, rag.promptBlock());
        return ragChatClient.prompt()
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .user(userMessage)
                .call()
                .content();
    }

    /**
     * search：复用 toolChatClient，强调优先使用搜索 / 天气等 MCP 工具
     */
    private String handleSearch(String input, String workDir) {
        String conversationId = "agent-route-search-" + UUID.randomUUID();
        String root = StringUtils.hasText(workDir)
                ? workDir.trim()
                : workspaceUtil.getWorkspaceRoot().toString();
        WorkspaceContext.set(root);
        try {
            return toolChatClient.prompt()
                    .system(com.mychat.utils.SearchSystemPrompts.SEARCH)
                    .user(input)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                    .call()
                    .content();
        } finally {
            WorkspaceContext.clear();
        }
    }

    /**
     * general：无工具纯对话
     */
    private String handleGeneral(String input) {
        return agentWorkflowChatClient.prompt()
                .system("你是友好的助手，用简洁中文回答用户的一般问题。")
                .user(input)
                .call()
                .content();
    }
}
