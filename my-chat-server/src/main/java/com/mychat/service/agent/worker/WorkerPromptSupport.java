package com.mychat.service.agent.worker;

import com.mychat.service.knowledge.KnowledgeRetrievalService;
import org.springframework.util.StringUtils;

/**
 * Worker 共用的会话摘要截断与 user 消息拼装（纯函数，不写 Memory）。
 */
public final class WorkerPromptSupport {

    /**
     * Worker 侧只读会话摘要预算（短于决策器侧，避免挤占工具上下文）。
     */
    public static final int WORKER_DIALOGUE_HISTORY_MAX_CHARS = 3000;

    private WorkerPromptSupport() {
    }

    /**
     * Worker user 消息：只读会话摘要 + 本步任务。
     * <p>
     * 不写入会话 Memory；仅消解「刚才/那个文件」等指代。
     */
    public static String buildWorkerUserMessage(String dialogueHistory, String instruction) {
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
    public static String truncateDialogueForWorker(String dialogueHistory) {
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
     * 生成知识库检索需要使用的 query
     */
    public static String kbSearchQuery(String userGoal, String instruction) {
        // 优先用用户原问题 userGoal, 这个为空才退回 instruction
        // StringUtils.hasText 用于判空
        if (StringUtils.hasText(userGoal)) {
            return userGoal.trim();
        }
        return StringUtils.hasText(instruction) ? instruction.trim() : "";
    }

    /**
     * 正式提示词：会话任务 + 已检索上下文
     */
    public static String buildKbWorkerUserPrompt(String workerUserMessage, String ragContext) {
        return KnowledgeRetrievalService.wrapUserWithContext(workerUserMessage, ragContext);
    }
}
