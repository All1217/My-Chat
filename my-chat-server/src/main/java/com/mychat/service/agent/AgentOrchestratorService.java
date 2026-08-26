package com.mychat.service.agent;

import com.mychat.common.ChatStreamEvent;
import com.mychat.common.OrchestratorWorkflow;
import com.mychat.config.WorkspaceContext;
import com.mychat.entity.dto.KnowledgeRetrieveHit;
import com.mychat.entity.dto.OrchestrateRequest;
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
import java.util.Locale;
import java.util.UUID;

/**
 * 回合内 Orchestrator-Workers 编排服务。
 * <p>
 * 外层：{@link OrchestratorWorkflow} 逐步产出 next_action；
 * 内层：Java switch 调用专用 ChatClient（与 Routing 同源能力档，不合并 Tools+RAG，遵守 P0-7）。
 * <p>
 * 会话 Memory：<b>读</b>＝{@link OrchestrateDialogueContextService} 注入摘要+近期原文；
 * Worker 仍用 {@code orch-*} 临时 conversationId，<b>不</b>挂 {@code MessageChatMemoryAdvisor} 写会话 chatId。
 * <b>写</b>＝主聊天回合结束由 {@code ChatOrchestrateStreamService} 手动 persist。
 * <p>
 * 调试 API 与主聊天共用本服务；主路传入 {@link OrchestrateListener} 推送 NDJSON {@code step}。
 * <p>
 * 最终答复写入会话 Memory / 主气泡依赖 {@link #resolveFinalAnswer}：finish 提纲过短时
 * 会用各步 observation 合成 Markdown，避免干货只留在时间线。
 * <p>
 * 主聊天 NDJSON 在编排结束后再经 {@link #streamFinalAnswer} 做 <b>token 级</b> {@code text_delta}；
 * 调试同步 API 仍直接使用 {@code finalAnswer} 字段。
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

    /**
     * 流式最终答复 prompt 中材料区总预算，防止上下文爆炸
     */
    public static final int STREAM_FINAL_MATERIALS_MAX_CHARS = 12000;

    private static final String FINAL_STREAM_SYSTEM = """
            你是面向用户的助手。根据「用户目标」「参考材料」和「答复草稿」，直接输出最终答复正文。
            要求：
            1. 默认 Markdown（标题、列表、代码块）；用户另有格式要求时除外。
            2. 把材料中的关键内容写进答复；不要只给「向用户作答 / 首先给出…」这类提纲或元指令。
            3. 草稿若已完整，可润色后输出；若草稿偏弱，必须基于材料写完整答复。
            4. 只输出用户可见正文，不要前言、不要解释你在流式输出。
            """;

    private static final String SEARCH_SYSTEM_PROMPT = SearchSystemPrompts.SEARCH;

    /**
     * finish.instruction 像「答题提纲」而非用户正文的常见元叙述
     */
    private static final List<String> META_FINAL_MARKERS = List.of(
            "向用户完整作答",
            "向用户作答",
            "完整作答",
            "首先给出",
            "接着提供",
            "最后给出",
            "然后给出",
            "结合知识库定义和搜索",
            "请按以下结构回答",
            "按如下结构回答"
    );

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

            log.info("Orchestrator step={}/{} action={} complexity={} reasoning={}",
                    i, maxSteps, action, complexity, reasoning);

            if ("finish".equals(action)) {
                String finalAnswer = resolveFinalAnswer(userGoal, instruction, steps);
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
                outcome = runWorker(action, instruction, kbId, workDir, dialogueHistory, userGoal);
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
            notifyListener(listener, step);

            // 单步快路径：明显单能力任务跑完一个成功 Worker 后直接 finish，不再二次 decide
            if (shouldAutoFinishAfterWorker(complexity, observation)) {
                String finalAnswer = resolveFinalAnswer(userGoal, null, steps);
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
        String finalAnswer = resolveFinalAnswer(userGoal, null, steps);
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
     * 按 action 调用对应 Worker。retrieve_kb 的检索 query 用用户原问，不用历史拼装消息。
     */
    private WorkerOutcome runWorker(
            String action, String instruction, String kbId, String workDir, String dialogueHistory, String userGoal) {
        String task = buildWorkerUserMessage(dialogueHistory, instruction);
        return switch (action) {
            case "retrieve_kb" -> workerKb(task, kbId, userGoal, instruction);
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
     * kb：自行 similaritySearch（query=用户原问），再交给 ragChatClient 生成。
     */
    private WorkerOutcome workerKb(String userMessage, String kbId, String userGoal, String instruction) {
        String conversationId = "orch-kb-" + UUID.randomUUID();
        // 1. 检索：query=用户原问，产出 prompt 与结构化来源
        String searchQuery = kbSearchQuery(userGoal, instruction);
        KnowledgeRetrievalService.RagContext rag = knowledgeRetrievalService.buildRagContext(kbId, searchQuery);
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
     * 内部先用 {@link #resolveFinalAnswer} 得到草稿（含弱答案 observation 合成），再 stream 润色输出。
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
            String finishInst = extractFinishInstruction(steps);
            draft = resolveFinalAnswer(userGoal, finishInst, steps);
        }
        String userPrompt = buildFinalAnswerStreamUserPrompt(userGoal, draft, steps, dialogueHistory);
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

    /**
     * 构建流式最终答复的 user 消息（含材料截断、可选会话摘要）。包可见便于单测。
     */
    static String buildFinalAnswerStreamUserPrompt(
            String userGoal, String draft, List<OrchestrateStepVO> steps, String dialogueHistory) {
        StringBuilder sb = new StringBuilder();
        sb.append("用户目标：\n")
                .append(StringUtils.hasText(userGoal) ? userGoal.trim() : "（未提供）")
                .append("\n\n");
        sb.append("会话上下文（只读参考，含摘要+近期原文；不要复述整段历史）：\n");
        sb.append(truncateDialogueForWorker(dialogueHistory));
        sb.append("\n\n");
        sb.append("参考材料（各 Worker observation，可能截断）：\n");
        sb.append(formatMaterialsForStreamPrompt(steps));
        sb.append("\n\n答复草稿：\n");
        sb.append(StringUtils.hasText(draft) ? draft.trim() : "（无草稿，请仅根据材料作答）");
        sb.append("\n\n请直接输出最终答复：");
        return sb.toString();
    }

    private static String formatMaterialsForStreamPrompt(List<OrchestrateStepVO> steps) {
        List<OrchestrateStepVO> usable = collectUsableObservationSteps(steps);
        if (usable.isEmpty()) {
            return "（无可用材料）\n";
        }
        StringBuilder sb = new StringBuilder();
        int remaining = STREAM_FINAL_MATERIALS_MAX_CHARS;
        for (OrchestrateStepVO s : usable) {
            if (remaining <= 0) {
                sb.append("…（后续材料已省略）\n");
                break;
            }
            String body = stripLeadingReasoningNoise(s.getObservation());
            if (!StringUtils.hasText(body)) {
                continue;
            }
            String header = "### " + sectionHeading(s.getAction()) + "（step " + s.getIndex() + "）\n";
            int budget = Math.min(remaining, Math.max(500, remaining / Math.max(1, usable.size())));
            if (body.length() > budget) {
                body = body.substring(0, budget) + "\n…[已截断]";
            }
            sb.append(header).append(body.trim()).append("\n\n");
            remaining -= header.length() + body.length();
        }
        return sb.isEmpty() ? "（无可用材料）\n" : sb.toString();
    }

    private static String extractFinishInstruction(List<OrchestrateStepVO> steps) {
        if (steps == null) {
            return "";
        }
        for (int i = steps.size() - 1; i >= 0; i--) {
            OrchestrateStepVO s = steps.get(i);
            if (s != null && "finish".equals(s.getAction()) && StringUtils.hasText(s.getInstruction())) {
                return s.getInstruction().trim();
            }
        }
        return "";
    }

    /**
     * 决议面向用户的最终答复：完整 finish 文案优先；提纲/过短则用 observation 合成 Markdown。
     */
    static String resolveFinalAnswer(String userGoal, String finishInstruction, List<OrchestrateStepVO> steps) {
        List<OrchestrateStepVO> usable = collectUsableObservationSteps(steps);
        int obsChars = usable.stream()
                .mapToInt(s -> s.getObservation() != null ? s.getObservation().length() : 0)
                .sum();

        if (StringUtils.hasText(finishInstruction)) {
            String trimmed = finishInstruction.trim();
            if (!isWeakFinalAnswer(trimmed, obsChars, usable)) {
                return trimmed;
            }
            log.info("finish.instruction 判定为弱最终答复（len={} obsChars={}），改用 observation 合成",
                    trimmed.length(), obsChars);
        }

        String composed = composeFinalAnswerFromSteps(userGoal, usable);
        if (StringUtils.hasText(composed)) {
            return composed;
        }

        // 仍无有效 observation：退回 finish 原文或空
        return StringUtils.hasText(finishInstruction) ? finishInstruction.trim() : "";
    }

    /**
     * 弱最终答复：元提纲、或明显短于已有 Worker 干货。
     */
    static boolean isWeakFinalAnswer(String instruction, int observationChars, List<OrchestrateStepVO> usable) {
        if (!StringUtils.hasText(instruction)) {
            return true;
        }
        String text = instruction.trim();
        String lower = text.toLowerCase(Locale.ROOT);

        for (String marker : META_FINAL_MARKERS) {
            if (text.contains(marker)) {
                return true;
            }
        }
        // 英文元叙述（模型偶发）
        if (lower.contains("tell the user") || lower.contains("provide the user")
                || lower.contains("respond to the user with")) {
            return true;
        }

        if (usable != null && !usable.isEmpty() && observationChars > 800 && text.length() < 200) {
            return true;
        }
        if (usable != null && !usable.isEmpty() && observationChars > text.length() * 3L && text.length() < 400) {
            return true;
        }

        // observation 已有大量 Markdown/代码，而 finish 几乎无结构 → 更像提纲
        boolean obsHasMd = usable != null && usable.stream().anyMatch(s -> looksLikeMarkdownBody(s.getObservation()));
        if (obsHasMd && !looksLikeMarkdownBody(text) && text.length() < Math.min(observationChars, 600)) {
            return true;
        }
        return false;
    }

    private static boolean looksLikeMarkdownBody(String s) {
        if (!StringUtils.hasText(s)) {
            return false;
        }
        return s.contains("\n## ") || s.contains("\n# ") || s.contains("```")
                || s.contains("\n- ") || s.contains("\n* ") || s.contains("|---");
    }

    private static List<OrchestrateStepVO> collectUsableObservationSteps(List<OrchestrateStepVO> steps) {
        List<OrchestrateStepVO> out = new ArrayList<>();
        if (steps == null) {
            return out;
        }
        for (OrchestrateStepVO s : steps) {
            if (s == null || "finish".equals(s.getAction())) {
                continue;
            }
            String obs = s.getObservation();
            if (!StringUtils.hasText(obs)) {
                continue;
            }
            String t = obs.trim();
            if (t.startsWith("[约束]") || t.startsWith("[Worker 错误]")) {
                continue;
            }
            out.add(s);
        }
        return out;
    }

    /**
     * 按 Worker 类型分段拼 Markdown，供主气泡 / spring_ai_chat_memory 使用。
     */
    static String composeFinalAnswerFromSteps(String userGoal, List<OrchestrateStepVO> usable) {
        if (usable == null || usable.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(userGoal)) {
            sb.append("针对你的问题「").append(trimOneLine(userGoal, 120)).append("」，整理如下：\n\n");
        }

        boolean any = false;
        for (OrchestrateStepVO s : usable) {
            String heading = sectionHeading(s.getAction());
            String body = stripLeadingReasoningNoise(s.getObservation());
            if (!StringUtils.hasText(body)) {
                continue;
            }
            any = true;
            sb.append("## ").append(heading).append("\n\n");
            sb.append(body.trim()).append("\n\n");
        }
        return any ? sb.toString().trim() : "";
    }

    private static String sectionHeading(String action) {
        if (action == null) {
            return "结果";
        }
        return switch (action) {
            case "retrieve_kb" -> "知识库要点";
            case "search" -> "联网补充";
            case "file" -> "文件结果";
            case "general" -> "分析说明";
            default -> "步骤结果（" + action + "）";
        };
    }

    /**
     * 去掉偶发夹在 observation 前的英文思考句，保留正文
     */
    private static String stripLeadingReasoningNoise(String observation) {
        if (!StringUtils.hasText(observation)) {
            return "";
        }
        String t = observation.trim();
        // 常见：英文过渡句 + 空行 + Markdown 正文
        int md = indexOfMarkdownStart(t);
        if (md > 0 && md < 400) {
            return t.substring(md).trim();
        }
        return t;
    }

    private static int indexOfMarkdownStart(String t) {
        int best = -1;
        for (String marker : List.of("\n# ", "\n## ", "\n```", "\n---\n")) {
            int i = t.indexOf(marker);
            if (i >= 0 && (best < 0 || i < best)) {
                best = i + 1; // skip leading \n
            }
        }
        if (t.startsWith("# ") || t.startsWith("## ") || t.startsWith("```")) {
            return 0;
        }
        return best;
    }

    private static String trimOneLine(String s, int max) {
        String one = s.replace('\n', ' ').replace('\r', ' ').trim();
        if (one.length() <= max) {
            return one;
        }
        return one.substring(0, max) + "…";
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
