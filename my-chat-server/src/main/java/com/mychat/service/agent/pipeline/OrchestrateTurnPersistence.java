package com.mychat.service.agent.pipeline;

import com.mychat.common.ChatStreamEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 编排回合写路径：短 USER + 拼接后的 ASSISTANT 正文写入 spring_ai_chat_memory。
 * <p>
 * 怎么读：仅 {@link ChatTurnFinalizer} 在 {@code ON_COMPLETE} 时调用；不写附件正文。
 */
@Slf4j
@Component
public class OrchestrateTurnPersistence {

    private final ChatMemory chatMemory;

    public OrchestrateTurnPersistence(ChatMemory chatMemory) {
        this.chatMemory = chatMemory;
    }

    /**
     * 从 accumulated 的 text_delta 拼 ASSISTANT，与短 USER 一并落入会话 Memory。
     */
    public void persist(ChatTurnContext ctx) {
        persistOrchestrateExchange(ctx.getChatId(), ctx.getMemoryUserText(), ctx.getAccumulated());
    }

    /**
     * 拼 text_delta → chatMemory.add；空正文或缺参则跳过。
     *
     * @param chatId     会话 ID
     * @param userPrompt 短 USER（文件名+原问）
     * @param events     本回合已推事件
     */
    public void persistOrchestrateExchange(String chatId, String userPrompt, List<ChatStreamEvent> events) {
        if (!StringUtils.hasText(chatId) || !StringUtils.hasText(userPrompt) || events == null) {
            return;
        }
        // 与流式路径一致：保留空格/换行 delta，勿用 hasText 丢掉 Markdown 结构空白
        StringBuilder assistant = new StringBuilder();
        for (ChatStreamEvent e : events) {
            if (e != null
                    && ChatStreamEvent.TYPE_TEXT_DELTA.equals(e.type())
                    && e.text() != null
                    && !e.text().isEmpty()) {
                assistant.append(e.text());
            }
        }
        if (assistant.isEmpty()) {
            return;
        }
        try {
            chatMemory.add(chatId, List.of(
                    new UserMessage(userPrompt),
                    new AssistantMessage(assistant.toString())));
        } catch (Exception e) {
            log.error("编排回合写入会话 Memory 失败 chatId={}: {}", chatId, e.getMessage(), e);
        }
    }
}
