package com.mychat.service.agent;

import com.mychat.vo.OrchestrateStepVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 编排结束后的最终答复拼装：弱提纲判定、observation 合成 Markdown、流式润色用 user prompt。
 * <p>
 * 纯函数，无 Spring / ChatClient。流式 {@code ChatClient.stream()} 仍在 {@link AgentOrchestratorService#streamFinalAnswer}。
 */
@Slf4j
public final class FinalAnswerComposer {

    /**
     * 流式最终答复 prompt 中材料区总预算，防止上下文爆炸。
     */
    public static final int STREAM_FINAL_MATERIALS_MAX_CHARS = 12000;

    /**
     * finish.instruction 像「答题提纲」而非用户正文的常见元叙述。
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

    private FinalAnswerComposer() {
    }

    /**
     * 决议面向用户的最终答复：完整 finish 文案优先；提纲/过短则用 observation 合成 Markdown。
     */
    public static String resolveFinalAnswer(
            String userGoal, String finishInstruction, List<OrchestrateStepVO> steps) {
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
    public static boolean isWeakFinalAnswer(
            String instruction, int observationChars, List<OrchestrateStepVO> usable) {
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

    /**
     * 按 Worker 类型分段拼 Markdown，供主气泡 / spring_ai_chat_memory 使用。
     */
    public static String composeFinalAnswerFromSteps(String userGoal, List<OrchestrateStepVO> usable) {
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

    /**
     * 构建流式最终答复的 user 消息（含材料截断、可选会话摘要）。
     */
    public static String buildFinalAnswerStreamUserPrompt(
            String userGoal, String draft, List<OrchestrateStepVO> steps, String dialogueHistory) {
        StringBuilder sb = new StringBuilder();
        sb.append("用户目标：\n")
                .append(StringUtils.hasText(userGoal) ? userGoal.trim() : "（未提供）")
                .append("\n\n");
        sb.append("会话上下文（只读参考，含摘要+近期原文；不要复述整段历史）：\n");
        sb.append(AgentOrchestratorService.truncateDialogueForWorker(dialogueHistory));
        sb.append("\n\n");
        sb.append("参考材料（各 Worker observation，可能截断）：\n");
        sb.append(formatMaterialsForStreamPrompt(steps));
        sb.append("\n\n答复草稿：\n");
        sb.append(StringUtils.hasText(draft) ? draft.trim() : "（无草稿，请仅根据材料作答）");
        sb.append("\n\n请直接输出最终答复：");
        return sb.toString();
    }

    /**
     * 从步骤列表取最后一条 finish.instruction，供 sync 未填 finalAnswer 时补草稿。
     */
    static String extractFinishInstruction(List<OrchestrateStepVO> steps) {
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

    /** 把各步 observation 编进流式 prompt 材料区，按预算截断。 */
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

    private static boolean looksLikeMarkdownBody(String s) {
        if (!StringUtils.hasText(s)) {
            return false;
        }
        return s.contains("\n## ") || s.contains("\n# ") || s.contains("```")
                || s.contains("\n- ") || s.contains("\n* ") || s.contains("|---");
    }

    /** 去掉 finish / 约束 / 空观察，只留可合成的 Worker 干货。 */
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

    /** 去掉偶发夹在 observation 前的英文思考句，保留正文。 */
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
}
