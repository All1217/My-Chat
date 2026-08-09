package com.mychat.common;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Orchestrator-Workers：每步用一次 LLM（Structured Output）决定 next_action，
 * 由调用方 Java {@code switch} 分发到专用 ChatClient Worker。
 * <p>
 * 与 {@link RoutingWorkflow} 的区别：Routing 只分类一次；本类可在回合内多轮决策，
 * 从而支持 kb → search / kb → file 等跨能力接力。
 */
public class OrchestratorWorkflow {

    public static final Set<String> ALLOWED_ACTIONS = Set.of(
            "retrieve_kb", "file", "search", "general", "finish");

    /**
     * 编排器 Structured Output。
     *
     * @param reasoning  为何选该动作
     * @param nextAction 动作标签，须为 {@link #ALLOWED_ACTIONS} 之一
     * @param instruction 给 Worker 的子任务；{@code finish} 时必须是面向用户的完整最终答复
     */
    public record NextAction(String reasoning, String nextAction, String instruction) {
    }

    /**
     * 已执行步骤摘要，供下一轮编排 prompt 使用。
     */
    public record StepSummary(int index, String action, String instruction, String observation) {
    }

    private final ChatClient chatClient;

    public OrchestratorWorkflow(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * 根据用户目标与历史观察，决定下一步动作。
     *
     * @param userGoal         用户原问
     * @param kbId             可选知识库
     * @param workDir          可选工作目录
     * @param priorSteps       本回合已完成步骤（可空）
     * @param stepIndex        即将执行的步号（从 1 起）
     * @param maxSteps         本回合上限
     * @param dialogueHistory  会话级多轮对话摘要（可空；与 priorSteps 不同）
     */
    @SuppressWarnings("null")
    public NextAction decideNext(
            String userGoal,
            String kbId,
            String workDir,
            List<StepSummary> priorSteps,
            int stepIndex,
            int maxSteps,
            String dialogueHistory) {

        String sessionHints = buildSessionHints(kbId, workDir);
        String history = formatHistory(priorSteps);
        String dialogue = StringUtils.hasText(dialogueHistory)
                ? dialogueHistory.trim()
                : "（尚无更早的会话消息）";

        // 字面量花括号须写成 \{ \}，否则 StTemplateRenderer 会把 JSON 示例当成模板变量
        String prompt = """
                你是任务编排器（Orchestrator）。根据用户目标、近期对话与已完成步骤，决定下一步动作。
                不要自己调用工具；只输出 JSON 决策，由系统调度专用 Worker。

                可选 nextAction（必须是下列英文标签之一）：
                - retrieve_kb：从已绑定知识库检索并回答子问题（需要可用 kbId）
                - file：查看/创建/修改工作区文件
                - search：联网搜索、查天气或其它远程 MCP 信息
                - general：无需检索/文件/联网的一般推理或润色
                - finish：任务已完成；instruction 必须是给用户看的完整最终答复

                规则：
                - 复杂任务拆成多步；非 finish 时 instruction 只描述当前 Worker 要做的事。
                - 若用户追问「刚才/上一条/之前说了什么」等，必须依据「近期对话」回答，禁止声称没有历史。
                - 给 Worker 的 instruction 必要时应带上指代消解后的完整上下文。
                - 若「本回合已完成步骤」的 observation 已足够回答用户，选 finish。
                - **finish 的 instruction（强制）**：
                  1. 必须是可直接展示给用户的完整答复正文，不是「向用户完整作答 / 首先给出 / 接着提供」等提纲或元指令。
                  2. 默认使用 Markdown（标题、列表、代码块）；仅当用户明确要求纯文本/JSON 等时才改格式。
                  3. 必须把各步 observation 中的关键内容（知识库定义、案例代码、搜索结论、文件结果）写入答复；用户默认不会展开时间线。
                  4. 若某步 observation 含「已截断」提示，仍基于可见内容尽量完整作答，并可注明可能有省略。
                - 当前是第 {stepIndex} 步，最多 {maxSteps} 步；临近上限时优先 finish。
                - 未绑定知识库时禁止选 retrieve_kb。

                会话上下文：
                {sessionHints}

                近期对话（会话级，按时间从早到晚）：
                {dialogueHistory}

                本回合已完成步骤：
                {history}

                用户本轮目标：
                {userGoal}

                按以下 JSON 返回（JSON 本身不要再包一层 markdown 代码块；但 finish 时 instruction 字符串内可以使用 Markdown）：
                \\{
                  "reasoning": "简要理由",
                  "nextAction": "retrieve_kb|file|search|general|finish",
                  "instruction": "Worker 子任务；或 finish 时的完整用户可见答复"
                \\}
                """;

        NextAction raw = chatClient.prompt()
                .user(u -> u.text(prompt)
                        .param("stepIndex", String.valueOf(stepIndex))
                        .param("maxSteps", String.valueOf(maxSteps))
                        .param("sessionHints", sessionHints)
                        .param("dialogueHistory", dialogue)
                        .param("history", history)
                        .param("userGoal", userGoal))
                .call()
                .entity(NextAction.class);

        String action = normalizeAction(raw != null ? raw.nextAction() : null);
        String reasoning = raw != null && raw.reasoning() != null ? raw.reasoning() : "";
        String instruction = raw != null && raw.instruction() != null ? raw.instruction() : "";

        if (!ALLOWED_ACTIONS.contains(action)) {
            reasoning = (reasoning.isBlank() ? "" : reasoning + " ")
                    + "[未知 nextAction，已回退为 general]";
            action = "general";
            if (!StringUtils.hasText(instruction)) {
                instruction = userGoal;
            }
        }

        return new NextAction(reasoning, action, instruction);
    }

    private static String buildSessionHints(String kbId, String workDir) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(kbId)) {
            sb.append("- 已绑定知识库 kbId=").append(kbId.trim())
                    .append("，需要文档依据时可选 retrieve_kb。\n");
        } else {
            sb.append("- 未绑定知识库：不要选择 retrieve_kb。\n");
        }
        if (StringUtils.hasText(workDir)) {
            sb.append("- 工作目录：").append(workDir.trim()).append("\n");
        } else {
            sb.append("- 未指定自定义工作目录，file 将使用默认工作区。\n");
        }
        return sb.toString().trim();
    }

    private static String formatHistory(List<StepSummary> priorSteps) {
        if (priorSteps == null || priorSteps.isEmpty()) {
            return "（尚无已完成步骤）";
        }
        StringBuilder sb = new StringBuilder();
        for (StepSummary s : priorSteps) {
            sb.append(s.index()).append(". action=").append(s.action())
                    .append("\n   instruction: ").append(nullToEmpty(s.instruction()))
                    .append("\n   observation: ").append(nullToEmpty(s.observation()))
                    .append("\n");
        }
        return sb.toString().trim();
    }

    private static String normalizeAction(String action) {
        if (action == null || action.isBlank()) {
            return "general";
        }
        return action.trim().toLowerCase(Locale.ROOT);
    }

    private static String nullToEmpty(String s) {
        return s != null ? s : "";
    }
}
