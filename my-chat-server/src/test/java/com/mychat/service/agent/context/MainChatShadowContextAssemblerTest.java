package com.mychat.service.agent.context;

import com.mychat.agent.context.DefaultContextAssembler;
import com.mychat.config.WorkspaceContext;
import com.mychat.service.agent.pipeline.ChatTurnContext;
import com.mychat.vo.OrchestrateResultVO;
import com.mychat.vo.OrchestrateStepVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 主聊天 shadow 对附件、记忆、KB、工具与显式 workDir 的装配回归。 */
class MainChatShadowContextAssemblerTest {

    private final MainChatShadowContextAssembler assembler =
            new MainChatShadowContextAssembler(new DefaultContextAssembler());

    @AfterEach
    void clearWorkspace() {
        WorkspaceContext.clear();
    }

    /** 现有主聊天输入被拆为独立上下文槽位，且不丢失附件。 */
    @Test
    void assemblesCurrentTurnIntoTypedSlots() {
        WorkspaceContext.set("E:/workspace/demo");
        ChatTurnContext ctx = ChatTurnContext.open(
                """
                上传了以下文件：
                - note.txt（10 B）

                以下为上传文档正文（供回答参考）：
                文档事实
                用户的问题：
                请总结
                """,
                "上传了以下文件：note.txt\n\n用户的问题：请总结",
                "请总结",
                "chat-1",
                "kb-1",
                false,
                null);
        ctx.setDialogueHistory("""
                【会话摘要｜较早轮次压缩，可能有损】
                之前讨论过架构。

                【近期对话原文】
                用户：继续
                """);
        OrchestrateStepVO kb = new OrchestrateStepVO(
                1, "retrieve_kb", "检索", "查询知识库", "知识库事实");
        OrchestrateStepVO search = new OrchestrateStepVO(
                2, "search", "搜索", "查询网页", "网页事实");
        ctx.setOrchestrateResult(new OrchestrateResultVO(
                "结果", "finish", List.of(kb, search)));

        MainChatShadowContextAssembler.ShadowAssembly shadow = assembler.assemble(ctx);

        assertEquals("E:/workspace/demo", shadow.runContext().workDir());
        assertEquals("kb-1", shadow.runContext().kbId());
        assertEquals("请总结", shadow.bundle().first("user.goal").content());
        assertTrue(shadow.bundle().first("upload.turn").content().contains("文档事实"));
        assertEquals("之前讨论过架构。", shadow.bundle().first("memory.summary").content());
        assertTrue(shadow.bundle().first("memory.recent").content().contains("用户：继续"));
        assertEquals("知识库事实", shadow.bundle().first("knowledge.context").content());
        assertEquals("网页事实", shadow.bundle().first("tool.observations").content());
        assertNotNull(shadow.bundle().fingerprint());
    }
}
