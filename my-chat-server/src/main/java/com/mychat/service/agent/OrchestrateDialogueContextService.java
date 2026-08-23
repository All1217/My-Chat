package com.mychat.service.agent;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mychat.entity.po.ChatSessionSummary;
import com.mychat.entity.po.SpringAiChatMemory;
import com.mychat.mapper.SpringAiChatMemoryMapper;
import com.mychat.service.chat.ChatSessionSummaryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * 编排读路径对话上下文：滚动摘要 + 近期原文窗口。
 * <p>
 * 不挂 {@code MessageChatMemoryAdvisor(chatId)}；写 Memory 仍由主聊天回合末手动 persist。
 * 摘要在本方法构建时惰性更新，避免与 Worker 临时 conversationId 双写。
 */
@Slf4j
@Service
public class OrchestrateDialogueContextService {

    /** 注入编排器的近期原文条数 */
    public static final int RECENT_MAX_MESSAGES = 10;
    /** 单条原文截断 */
    public static final int PER_MESSAGE_MAX_CHARS = 800;
    /** 近期原文总预算 */
    public static final int RECENT_TOTAL_MAX_CHARS = 4000;
    /** 未摘要旧消息达到该条数则触发滚动摘要 */
    public static final int SUMMARY_TRIGGER_MESSAGES = 6;
    /** 摘要正文上限 */
    public static final int SUMMARY_MAX_CHARS = 1800;

    private final SpringAiChatMemoryMapper springAiChatMemoryMapper;
    private final ChatSessionSummaryService chatSessionSummaryService;
    private final ChatClient agentWorkflowChatClient;

    public OrchestrateDialogueContextService(
            SpringAiChatMemoryMapper springAiChatMemoryMapper,
            ChatSessionSummaryService chatSessionSummaryService,
            @Qualifier("agentWorkflowChatClient") ChatClient agentWorkflowChatClient) {
        this.springAiChatMemoryMapper = springAiChatMemoryMapper;
        this.chatSessionSummaryService = chatSessionSummaryService;
        this.agentWorkflowChatClient = agentWorkflowChatClient;
    }

    /**
     * 构建注入 decideNext / Worker / streamFinal 的对话上下文字符串。
     *
     * @param chatId 会话 ID（与 spring_ai_chat_memory.conversation_id 一致）
     * @return 可空；无历史时返回 null
     */
    public String buildForOrchestrate(String chatId) {
        if (!StringUtils.hasText(chatId)) {
            return null;
        }
        String conversationId = chatId.trim();
        List<SpringAiChatMemory> all = loadUserAssistantRows(conversationId);
        if (all.isEmpty()) {
            return null;
        }

        // 按条数切分：尾部近期原文，头部为候选摘要区
        int recentCount = Math.min(RECENT_MAX_MESSAGES, all.size());
        int split = all.size() - recentCount;
        List<SpringAiChatMemory> older = split > 0 ? all.subList(0, split) : List.of();
        List<SpringAiChatMemory> recent = all.subList(split, all.size());
        // 获取摘要
        ChatSessionSummary existing = chatSessionSummaryService.getById(conversationId);
        long coveredUntil = existing != null && existing.getCoveredUntilSequenceId() != null
                ? existing.getCoveredUntilSequenceId()
                : 0L;

        List<SpringAiChatMemory> toFold = older.stream()
                .filter(m -> seqOf(m) > coveredUntil)
                .collect(Collectors.toList());

        String summaryText = existing != null && StringUtils.hasText(existing.getSummaryText())
                ? existing.getSummaryText().trim()
                : "";

        if (!toFold.isEmpty() && toFold.size() >= SUMMARY_TRIGGER_MESSAGES) {
            try {
                String updated = summarize(summaryText, toFold);
                long newCovered = seqOf(older.get(older.size() - 1));
                saveSummary(conversationId, updated, newCovered);
                summaryText = updated;
            } catch (Exception e) {
                log.warn("滚动摘要失败 conversationId={}: {}", conversationId, e.getMessage());
                // 失败则仍用旧摘要 + 近期原文
            }
        } else if (!toFold.isEmpty() && !StringUtils.hasText(summaryText) && older.size() >= SUMMARY_TRIGGER_MESSAGES) {
            // 首次：旧区已够长但从未摘要（例如刚超过窗口）
            try {
                String updated = summarize("", older);
                long newCovered = seqOf(older.get(older.size() - 1));
                saveSummary(conversationId, updated, newCovered);
                summaryText = updated;
            } catch (Exception e) {
                log.warn("首次滚动摘要失败 conversationId={}: {}", conversationId, e.getMessage());
            }
        }

        return formatContext(summaryText, recent);
    }

    /**
     * 纯拼接（不调 LLM），便于单测。包可见。
     */
    static String formatContext(String summaryText, List<SpringAiChatMemory> recent) {
        StringBuilder sb = new StringBuilder();
        sb.append("【会话摘要｜较早轮次压缩，可能有损】\n");
        if (StringUtils.hasText(summaryText)) {
            sb.append(summaryText.trim());
        } else {
            sb.append("（无）");
        }
        sb.append("\n\n【近期对话原文】\n");
        String recentBlock = formatRecent(recent);
        if (!StringUtils.hasText(recentBlock)) {
            sb.append("（无）");
        } else {
            sb.append(recentBlock);
        }
        return sb.toString().trim();
    }

    static String formatRecent(List<SpringAiChatMemory> recent) {
        if (recent == null || recent.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (SpringAiChatMemory m : recent) {
            if (m == null || !StringUtils.hasText(m.getContent())) {
                continue;
            }
            String role = roleLabel(m.getType());
            String text = m.getContent().trim();
            if (text.length() > PER_MESSAGE_MAX_CHARS) {
                text = text.substring(0, PER_MESSAGE_MAX_CHARS) + "…";
            }
            sb.append(role).append("：").append(text).append('\n');
            if (sb.length() >= RECENT_TOTAL_MAX_CHARS) {
                sb.setLength(RECENT_TOTAL_MAX_CHARS);
                sb.append("\n…（近期原文已截断）");
                break;
            }
        }
        return sb.toString().trim();
    }

    // 全量查询历史对话
    private List<SpringAiChatMemory> loadUserAssistantRows(String conversationId) {
        LambdaQueryWrapper<SpringAiChatMemory> q = new LambdaQueryWrapper<>();
        q.eq(SpringAiChatMemory::getConversationId, conversationId)
                .in(SpringAiChatMemory::getType, List.of("USER", "ASSISTANT", "user", "assistant"))
                .orderByAsc(SpringAiChatMemory::getSequenceId)
                .orderByAsc(SpringAiChatMemory::getId);
        List<SpringAiChatMemory> rows = springAiChatMemoryMapper.selectList(q);
        return rows != null ? rows : new ArrayList<>();
    }

    private String summarize(String previousSummary, List<SpringAiChatMemory> newOlder) {
        StringBuilder batch = new StringBuilder();
        for (SpringAiChatMemory m : newOlder) {
            batch.append(roleLabel(m.getType())).append("：")
                    .append(trimForSummary(m.getContent())).append('\n');
        }
        String userPrompt = """
                你是会话摘要器。把「已有摘要」与「新增较早对话」合并为一段简洁中文摘要，供后续编排消解指代。
                要求：保留文件路径、关键结论、用户明确约定；不要复述寒暄；不超过 %d 字；只输出摘要正文。
                
                【已有摘要】
                %s
                
                【新增较早对话】
                %s
                """.formatted(
                SUMMARY_MAX_CHARS,
                StringUtils.hasText(previousSummary) ? previousSummary : "（无）",
                batch.toString().trim());

        String content = agentWorkflowChatClient.prompt()
                .system("你只输出会话摘要正文，不要标题、不要解释。")
                .user(userPrompt)
                .call()
                .content();
        String out = content != null ? content.trim() : "";
        if (out.length() > SUMMARY_MAX_CHARS) {
            out = out.substring(0, SUMMARY_MAX_CHARS) + "…";
        }
        return out;
    }

    private void saveSummary(String conversationId, String summaryText, long coveredUntil) {
        ChatSessionSummary row = new ChatSessionSummary();
        row.setConversationId(conversationId);
        row.setSummaryText(summaryText != null ? summaryText : "");
        row.setCoveredUntilSequenceId(coveredUntil);
        row.setUpdatedAt(OffsetDateTime.now());
        chatSessionSummaryService.saveOrUpdate(row);
        log.info("已更新会话滚动摘要 conversationId={} coveredUntil={}", conversationId, coveredUntil);
    }

    private static long seqOf(SpringAiChatMemory m) {
        if (m == null || m.getSequenceId() == null) {
            return m != null && m.getId() != null ? m.getId() : 0L;
        }
        return m.getSequenceId();
    }

    private static String roleLabel(String type) {
        if (type == null) {
            return "未知";
        }
        String t = type.trim().toUpperCase(Locale.ROOT);
        if ("USER".equals(t)) {
            return "用户";
        }
        if ("ASSISTANT".equals(t)) {
            return "助手";
        }
        return type;
    }

    private static String trimForSummary(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        String t = content.trim();
        return t.length() > 500 ? t.substring(0, 500) + "…" : t;
    }
}
