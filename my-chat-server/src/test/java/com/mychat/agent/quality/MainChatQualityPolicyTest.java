package com.mychat.agent.quality;

import com.mychat.agent.api.AgentRunResult;
import com.mychat.agent.api.AgentStepResult;
import com.mychat.service.agent.pipeline.ChatTurnContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** MAIN_CHAT 质量环触发边界测试。 */
class MainChatQualityPolicyTest {

    private final MainChatQualityPolicy policy = new MainChatQualityPolicy();

    /** 文件类产物在启用后最多执行两轮质量优化。 */
    @Test
    void runsForImprovableFileOutputWithTwoRoundLimit() {
        ChatTurnContext turn = completedTurn("编写 README.md", true, "章节完整且示例可运行", Map.of());

        MainChatQualityPolicy.Decision decision = policy.decide(turn);

        assertTrue(decision.run());
        assertEquals(2, decision.maxIterations());
    }

    /** 普通回答和带高风险语义的任务不进入质量环。 */
    @Test
    void skipsNonFileAndHighRiskTasks() {
        ChatTurnContext plain = completedTurn("解释这段概念", false, "回答准确", Map.of());
        assertFalse(policy.decide(plain).run());

        ChatTurnContext risky = completedTurn(
                "发布交易报告.md", true, "报告完整", Map.of());
        assertFalse(policy.decide(risky).run());
    }

    /** 缺少显式 rubric 或剩余预算不足时不进入质量环。 */
    @Test
    void skipsWithoutRubricOrRemainingBudget() {
        assertFalse(policy.decide(completedTurn(
                "编写 README.md", true, null, Map.of())).run());
        assertFalse(policy.decide(completedTurn(
                "编写 README.md", true, "内容完整",
                Map.of("tokenUsage", 12_000L))).run());
    }

    /** 构造新 Runtime 已成功的回合。 */
    private ChatTurnContext completedTurn(
            String prompt, boolean wroteFile, String criteria, Map<String, Object> metrics) {
        ChatTurnContext turn = ChatTurnContext.open(
                prompt, prompt, prompt, "chat-1", null, true, criteria);
        List<AgentStepResult> steps = wroteFile
                ? List.of(new AgentStepResult(
                        1, "file", AgentStepResult.Status.SUCCEEDED,
                        "written", null, Map.of()))
                : List.of(new AgentStepResult(
                        1, "general", AgentStepResult.Status.SUCCEEDED,
                        "answer", null, Map.of()));
        turn.setAgentRunResult(new AgentRunResult(
                AgentRunResult.Status.SUCCEEDED, "done", steps, null,
                mergedMetrics(metrics)));
        return turn;
    }

    /** 合并基础耗时指标与测试覆盖指标。 */
    private Map<String, Object> mergedMetrics(Map<String, Object> metrics) {
        Map<String, Object> values = new java.util.HashMap<>();
        values.put("elapsedMs", 100L);
        values.putAll(metrics);
        return values;
    }
}
