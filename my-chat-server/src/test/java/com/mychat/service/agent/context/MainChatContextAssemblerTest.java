package com.mychat.service.agent.context;

import com.mychat.agent.context.DefaultContextAssembler;
import com.mychat.config.WorkspaceContext;
import com.mychat.service.agent.pipeline.ChatTurnContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** MAIN_CHAT 正式上下文装配回归测试。 */
class MainChatContextAssemblerTest {

    /** 清理测试线程工作区。 */
    @AfterEach
    void clearWorkspace() {
        WorkspaceContext.clear();
    }

    /** 附件只注入一次，摘要和近期原文使用独立槽位。 */
    @Test
    void assemblesUploadSummaryAndRecentWithoutDuplication() {
        WorkspaceContext.set("D:/workspace");
        ChatTurnContext turn = ChatTurnContext.open(
                "【上传文件：a.txt】\n附件唯一正文\n\n用户的问题：生成报告",
                "生成报告",
                "生成报告",
                "chat-1",
                "kb-1",
                false,
                null);
        turn.setDialogueHistory("""
                【会话摘要｜较早轮次压缩，可能有损】
                远期摘要
                【近期对话原文】
                USER: 近期原文
                """);

        MainChatContextAssembler.Assembly assembly =
                new MainChatContextAssembler(new DefaultContextAssembler()).assemble(turn);

        assertEquals("D:/workspace", assembly.runContext().workDir());
        assertTrue(assembly.runContext().runId().length() <= 48);
        assertTrue(assembly.bundle().blocks().stream()
                .anyMatch(block -> "upload.turn".equals(block.slot())));
        assertTrue(assembly.bundle().blocks().stream()
                .anyMatch(block -> "memory.summary".equals(block.slot())));
        assertTrue(assembly.bundle().blocks().stream()
                .anyMatch(block -> "memory.recent".equals(block.slot())));
        assertEquals(1, occurrences(assembly.renderedPrompt(), "附件唯一正文"));
    }

    /** 统计文本出现次数。 */
    private int occurrences(String source, String target) {
        return (source.length() - source.replace(target, "").length()) / target.length();
    }
}
