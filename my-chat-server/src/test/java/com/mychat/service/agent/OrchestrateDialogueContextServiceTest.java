package com.mychat.service.agent;

import com.mychat.entity.po.SpringAiChatMemory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrchestrateDialogueContextServiceTest {

    @Test
    void formatContextWithoutSummaryShowsPlaceholder() {
        SpringAiChatMemory u = row("USER", "你好");
        SpringAiChatMemory a = row("ASSISTANT", "你好，有什么可以帮你？");
        String ctx = OrchestrateDialogueContextService.formatContext("", List.of(u, a));
        assertTrue(ctx.contains("【会话摘要｜较早轮次压缩，可能有损】"));
        assertTrue(ctx.contains("（无）"));
        assertTrue(ctx.contains("【近期对话原文】"));
        assertTrue(ctx.contains("用户：你好"));
        assertTrue(ctx.contains("助手：你好，有什么可以帮你？"));
    }

    @Test
    void formatContextWithSummaryAndRecent() {
        String summary = "用户约定工作区为 D:/demo；曾讨论 README 结构。";
        SpringAiChatMemory u = row("USER", "刚才那个文件改好了吗");
        String ctx = OrchestrateDialogueContextService.formatContext(summary, List.of(u));
        assertTrue(ctx.contains(summary));
        assertTrue(ctx.contains("用户：刚才那个文件改好了吗"));
        assertFalse(ctx.contains("【会话摘要｜较早轮次压缩，可能有损】\n（无）"));
    }

    @Test
    void formatRecentTruncatesLongMessage() {
        String longText = "x".repeat(OrchestrateDialogueContextService.PER_MESSAGE_MAX_CHARS + 40);
        String recent = OrchestrateDialogueContextService.formatRecent(List.of(row("USER", longText)));
        assertTrue(recent.startsWith("用户："));
        assertTrue(recent.contains("…"));
        assertTrue(recent.length() < longText.length());
    }

    private static SpringAiChatMemory row(String type, String content) {
        SpringAiChatMemory m = new SpringAiChatMemory();
        m.setType(type);
        m.setContent(content);
        m.setSequenceId(1L);
        return m;
    }
}
