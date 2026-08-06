package com.mychat.service;

import com.mychat.common.ChatStreamEvent;
import com.mychat.common.OrchestratorWorkflow;
import com.mychat.config.WorkspaceContext;
import com.mychat.entity.dto.OrchestrateRequest;
import com.mychat.utils.WorkspaceUtil;
import com.mychat.vo.OrchestrateResultVO;
import com.mychat.vo.OrchestrateStepVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 回合内 Orchestrator-Workers 编排服务（同步，供调试 API）。
 * <p>
 * 外层：{@link OrchestratorWorkflow} 逐步产出 next_action；
 * 内层：Java switch 调用专用 ChatClient（与 Routing 同源能力档，不合并 Tools+RAG）。
 */
@Slf4j
@Service
public class AgentOrchestratorService {

    public static final int DEFAULT_MAX_STEPS = 6;
    public static final int MIN_MAX_STEPS = 1;
    public static final int HARD_MAX_STEPS = 8;

    private static final String SEARCH_SYSTEM_PROMPT = """
            当前请求已路由到 search。请优先调用可用的远程 MCP 工具
            （网页搜索、天气查询等）获取信息后再回答；不要编造实时数据。
            """;

    private final OrchestratorWorkflow orchestratorWorkflow;
    private final ChatClient toolChatClient;
    private final ChatClient ragChatClient;
    private final ChatClient agentWorkflowChatClient;
    private final AgentRoutingService agentRoutingService;
    private final WorkspaceUtil workspaceUtil;

    public AgentOrchestratorService(
            @Qualifier("agentWorkflowChatClient") ChatClient agentWorkflowChatClient,
            @Qualifier("toolChatClient") ChatClient toolChatClient,
            @Qualifier("ragChatClient") ChatClient ragChatClient,
            AgentRoutingService agentRoutingService,
            WorkspaceUtil workspaceUtil) {
        this.agentWorkflowChatClient = agentWorkflowChatClient;
        this.toolChatClient = toolChatClient;
        this.ragChatClient = ragChatClient;
        this.agentRoutingService = agentRoutingService;
        this.workspaceUtil = workspaceUtil;
        this.orchestratorWorkflow = new OrchestratorWorkflow(agentWorkflowChatClient);
    }

    /**
     * 执行编排循环直至 finish 或达到 maxSteps。
     *
     * @throws IllegalArgumentException input 为空等参数错误
     */
    public OrchestrateResultVO orchestrate(OrchestrateRequest request) {
        if (request == null || !StringUtils.hasText(request.getInput())) {
            throw new IllegalArgumentException("input 不能为空");
        }
        String userGoal = request.getInput().trim();
        String kbId = StringUtils.hasText(request.getKbId()) ? request.getKbId().trim() : null;
        String workDir = StringUtils.hasText(request.getWorkDir()) ? request.getWorkDir().trim() : null;
        int maxSteps = clampMaxSteps(request.getMaxSteps());

        List<OrchestrateStepVO> steps = new ArrayList<>();
        List<OrchestratorWorkflow.StepSummary> history = new ArrayList<>();

        for (int i = 1; i <= maxSteps; i++) {
            OrchestratorWorkflow.NextAction decision = orchestratorWorkflow.decideNext(
                    userGoal, kbId, workDir, history, i, maxSteps);
            String action = decision.nextAction();
            String reasoning = decision.reasoning() != null ? decision.reasoning() : "";
            String instruction = decision.instruction() != null ? decision.instruction() : "";

            log.info("Orchestrator step={}/{} action={} reasoning={}",
                    i, maxSteps, action, reasoning);

            if ("finish".equals(action)) {
                String finalAnswer = resolveFinalAnswer(instruction, steps);
                steps.add(new OrchestrateStepVO(i, "finish", reasoning, instruction, null));
                return new OrchestrateResultVO(finalAnswer, "finish", steps);
            }

            // 无 kbId 时禁止真正跑 RAG：记一步 invalid 观察，让编排器改选
            if ("retrieve_kb".equals(action) && !StringUtils.hasText(kbId)) {
                String obs = "[约束] 未提供 kbId，无法执行 retrieve_kb。请改选 general/search/file/finish。";
                appendStep(steps, history, i, "retrieve_kb", reasoning, instruction, obs);
                continue;
            }

            String observation;
            try {
                observation = runWorker(action, instruction, kbId, workDir);
            } catch (Exception e) {
                log.warn("Orchestrator Worker 失败 action={}: {}", action, e.getMessage());
                observation = "[Worker 错误] " + (e.getMessage() != null
                        ? e.getMessage()
                        : e.getClass().getSimpleName());
            }
            observation = truncateObservation(observation);
            appendStep(steps, history, i, action, reasoning, instruction, observation);
        }

        // 触顶：用最后观察或简短汇总作为答案
        String finalAnswer = resolveFinalAnswer(null, steps);
        if (!StringUtils.hasText(finalAnswer)) {
            finalAnswer = "已达到最大编排步数（" + maxSteps + "），未能产出完整答复。";
        }
        return new OrchestrateResultVO(finalAnswer, "max_steps", steps);
    }

    private String runWorker(String action, String instruction, String kbId, String workDir) {
        String task = StringUtils.hasText(instruction) ? instruction : "";
        return switch (action) {
            case "retrieve_kb" -> workerKb(task, kbId);
            case "file" -> workerFile(task, workDir);
            case "search" -> workerSearch(task, workDir);
            case "general" -> workerGeneral(task);
            default -> "[约束] 未实现的 Worker: " + action;
        };
    }

    private String workerKb(String instruction, String kbId) {
        String conversationId = "orch-kb-" + UUID.randomUUID();
        QuestionAnswerAdvisor qaAdvisor = agentRoutingService.buildKbAdvisor(kbId);
        String content = ragChatClient.prompt()
                .advisors(qaAdvisor)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .user(instruction)
                .call()
                .content();
        return content != null ? content : "";
    }

    private String workerFile(String instruction, String workDir) {
        String conversationId = "orch-file-" + UUID.randomUUID();
        String root = StringUtils.hasText(workDir)
                ? workDir
                : workspaceUtil.getWorkspaceRoot().toString();
        WorkspaceContext.set(root);
        try {
            String content = toolChatClient.prompt()
                    .system(buildWorkspaceSystemPrompt(root))
                    .user(instruction)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                    .call()
                    .content();
            return content != null ? content : "";
        } finally {
            WorkspaceContext.clear();
        }
    }

    private String workerSearch(String instruction, String workDir) {
        String conversationId = "orch-search-" + UUID.randomUUID();
        String root = StringUtils.hasText(workDir)
                ? workDir
                : workspaceUtil.getWorkspaceRoot().toString();
        WorkspaceContext.set(root);
        try {
            String content = toolChatClient.prompt()
                    .system(SEARCH_SYSTEM_PROMPT)
                    .user(instruction)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                    .call()
                    .content();
            return content != null ? content : "";
        } finally {
            WorkspaceContext.clear();
        }
    }

    private String workerGeneral(String instruction) {
        String content = agentWorkflowChatClient.prompt()
                .system("你是友好的助手，用简洁中文完成编排器交给你的子任务。不要尝试调用外部工具。")
                .user(instruction)
                .call()
                .content();
        return content != null ? content : "";
    }

    private static void appendStep(
            List<OrchestrateStepVO> steps,
            List<OrchestratorWorkflow.StepSummary> history,
            int index,
            String action,
            String reasoning,
            String instruction,
            String observation) {
        steps.add(new OrchestrateStepVO(index, action, reasoning, instruction, observation));
        history.add(new OrchestratorWorkflow.StepSummary(index, action, instruction, observation));
    }

    /**
     * finish：优先 instruction；否则取最后一条非空 observation。
     */
    private static String resolveFinalAnswer(String finishInstruction, List<OrchestrateStepVO> steps) {
        if (StringUtils.hasText(finishInstruction)) {
            return finishInstruction.trim();
        }
        for (int i = steps.size() - 1; i >= 0; i--) {
            String obs = steps.get(i).getObservation();
            if (StringUtils.hasText(obs) && !obs.startsWith("[约束]") && !obs.startsWith("[Worker 错误]")) {
                return obs.trim();
            }
        }
        for (int i = steps.size() - 1; i >= 0; i--) {
            if (StringUtils.hasText(steps.get(i).getObservation())) {
                return steps.get(i).getObservation().trim();
            }
        }
        return "";
    }

    static int clampMaxSteps(Integer requested) {
        if (requested == null) {
            return DEFAULT_MAX_STEPS;
        }
        return Math.max(MIN_MAX_STEPS, Math.min(HARD_MAX_STEPS, requested));
    }

    static String truncateObservation(String raw) {
        if (raw == null) {
            return "";
        }
        if (raw.length() <= ChatStreamEvent.PREVIEW_MAX_CHARS) {
            return raw;
        }
        return raw.substring(0, ChatStreamEvent.PREVIEW_MAX_CHARS) + "\n…[observation 已截断]";
    }

    private static String buildWorkspaceSystemPrompt(String workDir) {
        String name = Paths.get(workDir).getFileName() != null
                ? Paths.get(workDir).getFileName().toString()
                : workDir;
        return String.format("""
                所有涉及文件的查看、创建、写入、修改、删除、重命名、复制操作，务必积极调用可用工具实际执行。
                不能在回复中假装执行了文件操作。
                当前工作目录: %1$s
                路径规则：所有路径都是相对于当前工作目录的**相对路径**。
                不要把工作目录名 "%2$s" 作为路径前缀。
                """, workDir, name);
    }
}
