package com.mychat.service.agent;

import com.mychat.common.ChatStreamEvent;
import com.mychat.service.agent.workflow.OrchestratorWorkflow;
import com.mychat.config.WorkspaceContext;
import com.mychat.entity.dto.KnowledgeRetrieveHit;
import com.mychat.entity.dto.OrchestrateRequest;
import com.mychat.service.knowledge.KbScope;
import com.mychat.service.knowledge.KnowledgeRetrievalService;
import com.mychat.utils.SearchSystemPrompts;
import com.mychat.utils.WorkspacePromptBuilder;
import com.mychat.utils.WorkspaceUtil;
import com.mychat.vo.OrchestrateResultVO;
import com.mychat.vo.OrchestrateStepVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 回合内编排循环：decideNext → switch 调 Worker → 可选 auto-finish。
 * <p>
 * 怎么读：入口 {@code ChatController} → 管道 {@link ChatOrchestrateStreamService}
 * → 本类循环 → 本类 {@code workerKb}/{@code workerFile}/{@code workerSearch}/{@code workerGeneral}。
 * 主路固定 Orchestrator；Routing 是 Demo，不是本类的前置分类。
 * <p>
 * Worker 用 {@code orch-*} 临时 conversationId，不写会话 chatId。
 * Memory 读由 {@link OrchestrateDialogueContextService} 注入；写由管道在回合结束 persist。
 * 最终答复拼装见 {@link FinalAnswerComposer}；主路再经 {@link #streamFinalAnswer} 出 token 级 {@code text_delta}。
 */
@Slf4j
@Service
public class AgentOrchestratorService {

    public static final int DEFAULT_MAX_STEPS = 6;
    public static final int MIN_MAX_STEPS = 1;
    public static final int HARD_MAX_STEPS = 8;

    /**
     * 编排器 StepSummary / 合成兜底用的 observation 上限（大于 UI 预览，便于 finish 看见案例代码）。
     */
    public static final int HISTORY_OBSERVATION_MAX_CHARS = 16000;

    /**
     * Worker 侧只读会话摘要预算（短于决策器侧，避免挤占工具上下文）
     */
    public static final int WORKER_DIALOGUE_HISTORY_MAX_CHARS = 3000;

    private static final String FINAL_STREAM_SYSTEM = """
            你是面向用户的助手。根据「用户目标」「参考材料」和「答复草稿」，直接输出最终答复正文。
            要求：
            1. 默认 Markdown（标题、列表、代码块）；用户另有格式要求时除外。
            2. 把材料中的关键内容写进答复；不要只给「向用户作答 / 首先给出…」这类提纲或元指令。
            3. 草稿若已完整，可润色后输出；若草稿偏弱，必须基于材料写完整答复。
            4. 只输出用户可见正文，不要前言、不要解释你在流式输出。
            """;

    private static final String SEARCH_SYSTEM_PROMPT = SearchSystemPrompts.SEARCH;

    private final OrchestratorWorkflow orchestratorWorkflow;
    private final ChatClient toolChatClient;
    private final ChatClient ragChatClient;
    private final ChatClient agentWorkflowChatClient;
    private final KnowledgeRetrievalService knowledgeRetrievalService;
    private final WorkspaceUtil workspaceUtil;
    private final WorkspacePromptBuilder workspacePromptBuilder;

    public AgentOrchestratorService(
            @Qualifier("agentWorkflowChatClient") ChatClient agentWorkflowChatClient,
            @Qualifier("toolChatClient") ChatClient toolChatClient,
            @Qualifier("ragChatClient") ChatClient ragChatClient,
            KnowledgeRetrievalService knowledgeRetrievalService,
            WorkspaceUtil workspaceUtil,
            WorkspacePromptBuilder workspacePromptBuilder) {
        this.agentWorkflowChatClient = agentWorkflowChatClient;
        this.toolChatClient = toolChatClient;
        this.ragChatClient = ragChatClient;
        this.knowledgeRetrievalService = knowledgeRetrievalService;
        this.workspaceUtil = workspaceUtil;
        this.workspacePromptBuilder = workspacePromptBuilder;
        this.orchestratorWorkflow = new OrchestratorWorkflow(agentWorkflowChatClient);
    }

    /**
     * 同步编排（调试 API）；无逐步回调。
     */
    public OrchestrateResultVO orchestrate(OrchestrateRequest request) {
        return orchestrate(request, null);
    }

    /**
     * 执行编排循环直至 finish 或达到 maxSteps。
     *
     * @param listener 可为 null；非空时每步完成后回调（含 finish）
     * @throws IllegalArgumentException input 为空等参数错误
     */
    public OrchestrateResultVO orchestrate(OrchestrateRequest request, OrchestrateListener listener) {
        if (request == null || !StringUtils.hasText(request.getInput())) {
            throw new IllegalArgumentException("input 不能为空");
        }
        String userGoal = request.getInput().trim();
        String kbId = StringUtils.hasText(request.getKbId()) ? request.getKbId().trim() : null;
        String workDir = StringUtils.hasText(request.getWorkDir()) ? request.getWorkDir().trim() : null;
        String dialogueHistory = StringUtils.hasText(request.getDialogueHistory())
                ? request.getDialogueHistory().trim()
                : null;
        int maxSteps = clampMaxSteps(request.getMaxSteps());

        List<OrchestrateStepVO> steps = new ArrayList<>();
        List<OrchestratorWorkflow.StepSummary> history = new ArrayList<>();

        for (int i = 1; i <= maxSteps; i++) {
            OrchestratorWorkflow.NextAction decision = orchestratorWorkflow.decideNext(
                    userGoal, kbId, workDir, history, i, maxSteps, dialogueHistory);
            String action = decision.nextAction();
            String reasoning = decision.reasoning() != null ? decision.reasoning() : "";
            String instruction = decision.instruction() != null ? decision.instruction() : "";
            // finish 时忽略 complexity；Worker 步用 single 触发自动 finish（省第二次 decideNext）
            String complexity = OrchestratorWorkflow.normalizeComplexity(decision.complexity());
            String kbScope = OrchestratorWorkflow.normalizeKbScope(action, decision.kbScope());

            log.info("Orchestrator step={}/{} action={} complexity={} kbScope={} reasoning={}",
                    i, maxSteps, action, complexity, kbScope, reasoning);

            if ("finish".equals(action)) {
                String finalAnswer = FinalAnswerComposer.resolveFinalAnswer(userGoal, instruction, steps);
                // finish.observation = 已决议最终答复，时间线最终步与主气泡一致
                OrchestrateStepVO finishStep = new OrchestrateStepVO(
                        i, "finish", reasoning, instruction, finalAnswer);
                steps.add(finishStep);
                notifyListener(listener, finishStep);
                return new OrchestrateResultVO(finalAnswer, "finish", steps);
            }

            // 无 kbId 时禁止真正跑 RAG：记一步 invalid 观察，让编排器改选
            if ("retrieve_kb".equals(action) && !StringUtils.hasText(kbId)) {
                String obs = "[约束] 未提供 kbId，无法执行 retrieve_kb。请改选 general/search/file/finish。";
                OrchestrateStepVO step = appendStep(steps, history, i, "retrieve_kb", reasoning, instruction, obs);
                notifyListener(listener, step);
                continue;
            }

            WorkerOutcome outcome;
            try {
                outcome = runWorker(action, instruction, kbId, workDir, dialogueHistory, userGoal, kbScope);
            } catch (Exception e) {
                log.warn("Orchestrator Worker 失败 action={}: {}", action, e.getMessage());
                outcome = WorkerOutcome.text("[Worker 错误] " + (e.getMessage() != null
                        ? e.getMessage()
                        : e.getClass().getSimpleName()));
            }
            // 编排历史保留更长正文；UI 预览由 ChatStreamEvent.step 再截断
            String observation = truncateForHistory(outcome.observation());
            OrchestrateStepVO step = appendStep(steps, history, i, action, reasoning, instruction, observation);
            if (outcome.citations() != null && !outcome.citations().isEmpty()) {
                step.setCitations(outcome.citations());
            }
            if (kbScope != null) {
                step.setKbScope(kbScope);
            }
            notifyListener(listener, step);

            // 单步快路径：明显单能力任务跑完一个成功 Worker 后直接 finish，不再二次 decide
            if (shouldAutoFinishAfterWorker(complexity, observation)) {
                String finalAnswer = FinalAnswerComposer.resolveFinalAnswer(userGoal, null, steps);
                if (!StringUtils.hasText(finalAnswer)) {
                    finalAnswer = observation;
                }
                String finishReason = "single-shot: complexity=single，Worker 观察已足够作答";
                OrchestrateStepVO finishStep = new OrchestrateStepVO(
                        i + 1, "finish", finishReason, "", finalAnswer);
                steps.add(finishStep);
                notifyListener(listener, finishStep);
                log.info("Orchestrator single-shot auto-finish after step={} action={}", i, action);
                return new OrchestrateResultVO(finalAnswer, "finish", steps);
            }
        }

        // 触顶：用最后观察或步骤合成作为答案
        String finalAnswer = FinalAnswerComposer.resolveFinalAnswer(userGoal, null, steps);
        if (!StringUtils.hasText(finalAnswer)) {
            finalAnswer = "已达到最大编排步数（" + maxSteps + "），未能产出完整答复。";
        }
        return new OrchestrateResultVO(finalAnswer, "max_steps", steps);
    }

    private static void notifyListener(OrchestrateListener listener, OrchestrateStepVO step) {
        if (listener == null || step == null) {
            return;
        }
        try {
            listener.onStep(step);
        } catch (Exception e) {
            log.warn("OrchestrateListener.onStep 失败 step={}: {}", step.getIndex(), e.getMessage());
        }
    }

    /**
     * 单步快路径门控：complexity=single 且 observation 非约束/错误时，跳过后续 decideNext。
     */
    static boolean shouldAutoFinishAfterWorker(String complexity, String observation) {
        if (!"single".equals(OrchestratorWorkflow.normalizeComplexity(complexity))) {
            return false;
        }
        if (!StringUtils.hasText(observation)) {
            return false;
        }
        String t = observation.trim();
        return !t.startsWith("[约束]") && !t.startsWith("[Worker 错误]");
    }

    /**
     * Worker 观察及可选知识库引用来源。
     */
    private record WorkerOutcome(String observation, List<KnowledgeRetrieveHit> citations) {
        /** 无引用的纯文本观察。 */
        static WorkerOutcome text(String observation) {
            return new WorkerOutcome(observation != null ? observation : "", null);
        }
    }

    /**
     * 按 action 调用对应 Worker。retrieve_kb 的检索 query 用用户原问，范围由 kbScope 决定。
     */
    private WorkerOutcome runWorker(
            String action, String instruction, String kbId, String workDir,
            String dialogueHistory, String userGoal, String kbScope) {
        String task = buildWorkerUserMessage(dialogueHistory, instruction);
        return switch (action) {
            case "retrieve_kb" -> workerKb(task, kbId, userGoal, instruction, kbScope);
            case "file" -> WorkerOutcome.text(workerFile(task, workDir));
            case "search" -> WorkerOutcome.text(workerSearch(task, workDir));
            case "general" -> WorkerOutcome.text(workerGeneral(task));
            default -> WorkerOutcome.text("[约束] 未实现的 Worker: " + action);
        };
    }

    /**
     * Worker user 消息：只读会话摘要 + 本步任务。包可见便于单测。
     * <p>
     * 不写入会话 Memory；仅消解「刚才/那个文件」等指代。
     */
    static String buildWorkerUserMessage(String dialogueHistory, String instruction) {
        String hist = truncateDialogueForWorker(dialogueHistory);
        String task = StringUtils.hasText(instruction) ? instruction.trim() : "";
        return """
                【会话上下文｜只读参考，用于消解「刚才/那个文件」等指代；不要复述整段历史】
                %s
                
                【本步任务】
                %s
                """.formatted(hist, task);
    }

    /**
     * Worker / 最终流式侧截断：优先保留「会话摘要」段，再截「近期原文」。
     */
    static String truncateDialogueForWorker(String dialogueHistory) {
        if (!StringUtils.hasText(dialogueHistory)) {
            return "（无）";
        }
        String t = dialogueHistory.trim();
        if (t.length() <= WORKER_DIALOGUE_HISTORY_MAX_CHARS) {
            return t;
        }
        final String recentMarker = "【近期对话原文】";
        int recentIdx = t.indexOf(recentMarker);
        if (recentIdx > 0 && t.contains("【会话摘要")) {
            String head = t.substring(0, recentIdx);
            String recent = t.substring(recentIdx);
            int budgetForRecent = WORKER_DIALOGUE_HISTORY_MAX_CHARS - head.length() - 24;
            if (budgetForRecent < 120) {
                return t.substring(0, WORKER_DIALOGUE_HISTORY_MAX_CHARS) + "\n…[会话上下文已截断]";
            }
            if (recent.length() > budgetForRecent) {
                recent = recent.substring(0, budgetForRecent) + "\n…[近期原文已截断]";
            }
            return head + recent;
        }
        return t.substring(0, WORKER_DIALOGUE_HISTORY_MAX_CHARS) + "\n…[会话摘要已截断]";
    }

    /**
     * 知识库检索 query：只用用户原问，避免把会话历史拿去 embedding。
     */
    static String kbSearchQuery(String userGoal, String instruction) {
        if (StringUtils.hasText(userGoal)) {
            return userGoal.trim();
        }
        return StringUtils.hasText(instruction) ? instruction.trim() : "";
    }

    /**
     * 生成侧 user：会话任务 + 已检索上下文（不再交给 QuestionAnswerAdvisor）。
     */
    static String buildKbWorkerUserPrompt(String workerUserMessage, String ragContext) {
        return KnowledgeRetrievalService.wrapUserWithContext(workerUserMessage, ragContext);
    }

    /**
     * kb：自行 similaritySearch 或目录（由 kbScope 决定），再交给 ragChatClient 生成。
     */
    private WorkerOutcome workerKb(
            String userMessage, String kbId, String userGoal, String instruction, String kbScope) {
        String conversationId = "orch-kb-" + UUID.randomUUID();
        // 1. 检索：query=用户原问；范围由编排器 kbScope 决定
        String searchQuery = kbSearchQuery(userGoal, instruction);
        KbScope scope = KbScope.from(kbScope);
        KnowledgeRetrievalService.RagContext rag =
                knowledgeRetrievalService.buildRagContext(kbId, searchQuery, scope);
        String prompt = buildKbWorkerUserPrompt(userMessage, rag.promptBlock());
        // 2. 生成：临时 conversationId，不写会话 Memory
        String content = ragChatClient.prompt()
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .user(prompt)
                .call()
                .content();
        // 3. 带回 citations，供 step.args 落入气泡引用
        return new WorkerOutcome(content != null ? content : "", rag.citations());
    }

    private String workerFile(String userMessage, String workDir) {
        String conversationId = "orch-file-" + UUID.randomUUID();
        String root = StringUtils.hasText(workDir)
                ? workDir
                : workspaceUtil.getWorkspaceRoot().toString();
        WorkspaceContext.set(root);
        try {
            String content = toolChatClient.prompt()
                    .system(workspacePromptBuilder.build(root))
                    .user(userMessage)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                    .call()
                    .content();
            return content != null ? content : "";
        } finally {
            WorkspaceContext.clear();
        }
    }

    private String workerSearch(String userMessage, String workDir) {
        String conversationId = "orch-search-" + UUID.randomUUID();
        String root = StringUtils.hasText(workDir)
                ? workDir
                : workspaceUtil.getWorkspaceRoot().toString();
        WorkspaceContext.set(root);
        try {
            String content = toolChatClient.prompt()
                    .system(SEARCH_SYSTEM_PROMPT)
                    .user(userMessage)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                    .call()
                    .content();
            return content != null ? content : "";
        } finally {
            WorkspaceContext.clear();
        }
    }

    private String workerGeneral(String userMessage) {
        String content = agentWorkflowChatClient.prompt()
                .system("你是友好的助手，用简洁中文完成编排器交给你的子任务。不要尝试调用外部工具。")
                .user(userMessage)
                .call()
                .content();
        return content != null ? content : "";
    }

    private static OrchestrateStepVO appendStep(
            List<OrchestrateStepVO> steps,
            List<OrchestratorWorkflow.StepSummary> history,
            int index,
            String action,
            String reasoning,
            String instruction,
            String observation) {
        OrchestrateStepVO step = new OrchestrateStepVO(index, action, reasoning, instruction, observation);
        steps.add(step);
        history.add(new OrchestratorWorkflow.StepSummary(index, action, instruction, observation));
        return step;
    }

    /**
     * 主聊天：在编排结束后流式生成最终答复（每项为文本增量，供 NDJSON {@code text_delta}）。
     * <p>
     * 内部先用 {@link FinalAnswerComposer#resolveFinalAnswer} 得到草稿，再 stream 润色输出。
     *
     * @param dialogueHistory 会话近期对话（只读注入；可空）
     */
    public Flux<String> streamFinalAnswer(
            String userGoal, OrchestrateResultVO result, String dialogueHistory) {
        String draft = "";
        List<OrchestrateStepVO> steps = List.of();
        if (result != null) {
            if (StringUtils.hasText(result.getFinalAnswer())) {
                draft = result.getFinalAnswer().trim();
            }
            if (result.getSteps() != null) {
                steps = result.getSteps();
            }
        }
        // 若 sync 路径未填 finalAnswer，再决议一次草稿
        if (!StringUtils.hasText(draft)) {
            String finishInst = FinalAnswerComposer.extractFinishInstruction(steps);
            draft = FinalAnswerComposer.resolveFinalAnswer(userGoal, finishInst, steps);
        }
        String userPrompt = FinalAnswerComposer.buildFinalAnswerStreamUserPrompt(
                userGoal, draft, steps, dialogueHistory);
        log.debug("streamFinalAnswer promptChars={} draftChars={}",
                userPrompt.length(), draft != null ? draft.length() : 0);
        return agentWorkflowChatClient.prompt()
                .system(FINAL_STREAM_SYSTEM)
                .user(userPrompt)
                .stream()
                .content()
                // 只丢 null/空串；保留空格与换行 token，否则 Markdown「## 标题」会变成「##标题」
                .filter(s -> s != null && !s.isEmpty());
    }

    static int clampMaxSteps(Integer requested) {
        if (requested == null) {
            return DEFAULT_MAX_STEPS;
        }
        return Math.max(MIN_MAX_STEPS, Math.min(HARD_MAX_STEPS, requested));
    }

    /**
     * 编排历史 / 合成用截断
     */
    static String truncateForHistory(String raw) {
        return truncateTo(raw, HISTORY_OBSERVATION_MAX_CHARS);
    }

    /**
     * UI 预览截断（与 {@link ChatStreamEvent#PREVIEW_MAX_CHARS} 对齐）。
     * 保留包可见性，便于单测与旧调用方。
     */
    static String truncateObservation(String raw) {
        return truncateTo(raw, ChatStreamEvent.PREVIEW_MAX_CHARS);
    }

    private static String truncateTo(String raw, int max) {
        if (raw == null) {
            return "";
        }
        if (raw.length() <= max) {
            return raw;
        }
        return raw.substring(0, max) + "\n…[observation 已截断]";
    }

}
