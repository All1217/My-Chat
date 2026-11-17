package com.mychat.agent.context;

import com.mychat.agent.api.AgentRunContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ContextAssembler 的优先级、预算与 fingerprint 回归。 */
class DefaultContextAssemblerTest {

    private final DefaultContextAssembler assembler = new DefaultContextAssembler();
    private final AgentRunContext run = AgentRunContext.mainChat(
            "run-1", "chat-1", "kb-1", "E:/workspace");

    /** 高优先级块先拿预算，低优先级块可被完全丢弃。 */
    @Test
    void allocatesBudgetByPriority() {
        List<ContextBlock> blocks = List.of(
                block("tool.observations", 10, 20, "tool-abcdefghij"),
                block("user.goal", 100, 10, "goal-123456789"),
                block("upload.turn", 90, 20, "upload-abcdefghij"));

        ContextBundle bundle = assembler.assemble(
                run, blocks, new ContextBudgetPolicy(15, Map.of()));

        assertEquals("goal-12345", bundle.first("user.goal").content());
        assertEquals("uploa", bundle.first("upload.turn").content());
        assertNull(bundle.first("tool.observations"));
        assertEquals(15, bundle.budgetReport().retainedChars());
        assertTrue(bundle.budgetReport().entries().stream().anyMatch(e -> e.truncated()));
    }

    /** 相同语义内容跨 Windows/Unix 换行产生相同 fingerprint。 */
    @Test
    void fingerprintIsStableAcrossLineEndings() {
        ContextBundle windows = assembler.assemble(
                run,
                List.of(block("user.goal", 100, 100, "第一行\r\n第二行")),
                new ContextBudgetPolicy(100, Map.of()));
        ContextBundle unix = assembler.assemble(
                run,
                List.of(block("user.goal", 100, 100, "第一行\n第二行")),
                new ContextBudgetPolicy(100, Map.of()));
        ContextBundle changed = assembler.assemble(
                run,
                List.of(block("user.goal", 100, 100, "不同内容")),
                new ContextBudgetPolicy(100, Map.of()));

        assertEquals(windows.fingerprint(), unix.fingerprint());
        assertNotEquals(windows.fingerprint(), changed.fingerprint());
    }

    /** 建立测试上下文块。 */
    private static ContextBlock block(String slot, int priority, int maxChars, String content) {
        return new ContextBlock(
                slot,
                ContextBlock.Role.USER,
                ContextBlock.Source.READ_ONLY,
                priority,
                maxChars,
                content);
    }
}
