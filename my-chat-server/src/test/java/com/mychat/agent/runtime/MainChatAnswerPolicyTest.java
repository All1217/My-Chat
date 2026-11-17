package com.mychat.agent.runtime;

import com.mychat.agent.api.AgentRunContext;
import com.mychat.agent.api.AgentRunRequest;
import com.mychat.agent.api.AgentScenarioId;
import com.mychat.agent.api.AgentStepResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** MAIN_CHAT 最终回答策略测试。 */
class MainChatAnswerPolicyTest {

    /** 直接返回 Planner finish 正文，不产生第二次检索或模型调用。 */
    @Test
    void returnsFinishOutputDirectly() {
        MainChatAnswerPolicy policy = new MainChatAnswerPolicy();
        AgentRunRequest request = new AgentRunRequest(
                "用户问题",
                new AgentRunContext(
                        "run-1", AgentScenarioId.MAIN_CHAT, "chat-1",
                        null, "kb-1", "D:/workspace", "trace-1", Map.of()),
                Map.of());
        AgentDecision finish = new AgentDecision(
                "", "", Map.of(), true, "最终回答", "完成");

        String answer = policy.answer(request, finish, List.of(
                new AgentStepResult(
                        1, "retrieve_kb", AgentStepResult.Status.SUCCEEDED,
                        "检索结果", null, Map.of())));

        assertEquals("最终回答", answer);
    }
}
