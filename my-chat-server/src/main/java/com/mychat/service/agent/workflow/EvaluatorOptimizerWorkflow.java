package com.mychat.service.agent.workflow;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.util.StringUtils;

/**
 * Evaluator-Optimizer 工作流：任务内质量环（写盘 → 读回 → 评价 → 不合格再改）。
 * <p>
 * 用于单次任务中提升产物质量；不是离线 Agent 评测 / Benchmark。
 * 本类只负责「评价」这一次 Structured Output；生成与读盘由调用方完成。
 */
public class EvaluatorOptimizerWorkflow {

    /**
     * 评价器 Structured Output。
     *
     * @param pass      是否达到标准
     * @param feedback  未通过时给生成器的修改意见；通过时可空
     * @param reasoning 简要评审理由
     */
    public record Evaluation(boolean pass, String feedback, String reasoning) {
    }

    private final ChatClient chatClient;

    public EvaluatorOptimizerWorkflow(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * 对照目标与质量标准评价当前文件内容。
     *
     * @param goal      用户目标
     * @param criteria  质量标准
     * @param path      目标相对路径
     * @param content   已读回的文件内容（可截断）
     * @param iteration 当前迭代号（从 1 起）
     */
    @SuppressWarnings("null")
    public Evaluation evaluate(
            String goal,
            String criteria,
            String path,
            String content,
            int iteration) {

        String safeContent = content != null ? content : "";
        String safeGoal = goal != null ? goal : "";
        String safeCriteria = criteria != null ? criteria : "";
        String safePath = path != null ? path : "";

        // 字面量花括号须写成 \{ \}，否则 StTemplateRenderer 会把 JSON 示例当成模板变量
        String prompt = """
                你是任务内质量环的评价器（不是离线评测打分系统）。
                对照「目标」与「质量标准」审阅磁盘上已写入的文件内容，判断是否达标。
                若未达标，给出可执行的修改意见（feedback），供生成器下一轮覆盖写入同一文件。

                当前迭代：第 {iteration} 轮
                目标路径：{path}

                用户目标：
                {goal}

                质量标准：
                {criteria}

                文件当前内容：
                {content}

                按以下 JSON 返回（不要 markdown 代码块；pass 为布尔值）：
                \\{
                  "pass": true,
                  "feedback": "未通过时的具体修改意见，通过时可空字符串",
                  "reasoning": "简要评审理由"
                \\}
                """;

        Evaluation raw = chatClient.prompt()
                .user(u -> u.text(prompt)
                        .param("iteration", String.valueOf(iteration))
                        .param("path", safePath)
                        .param("goal", safeGoal)
                        .param("criteria", safeCriteria)
                        .param("content", safeContent))
                .call()
                .entity(Evaluation.class);

        if (raw == null) {
            return new Evaluation(false, "评价器未返回有效结果，请根据标准重写文件。", "evaluation null");
        }
        String feedback = raw.feedback() != null ? raw.feedback() : "";
        String reasoning = raw.reasoning() != null ? raw.reasoning() : "";
        if (!raw.pass() && !StringUtils.hasText(feedback)) {
            feedback = "未通过质量标准，请对照 criteria 完善文件内容后重新写入。";
        }
        return new Evaluation(raw.pass(), feedback, reasoning);
    }
}
