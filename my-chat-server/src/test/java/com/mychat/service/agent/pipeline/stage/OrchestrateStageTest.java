package com.mychat.service.agent.pipeline.stage;

import com.mychat.agent.api.AgentRunResult;
import com.mychat.service.agent.MainChatRuntimeService;
import com.mychat.service.agent.pipeline.ChatTurnContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 主聊天正式 Runtime 编排阶段测试。 */
class OrchestrateStageTest {

    /** Runtime 成功结果会写入回合上下文。 */
    @Test
    void storesMainChatRuntimeResult() {
        MainChatRuntimeService runtime = mock(MainChatRuntimeService.class);
        AgentRunResult result = new AgentRunResult(
                AgentRunResult.Status.SUCCEEDED, "new", List.of(), null, Map.of());
        ChatTurnContext turn = turn();
        when(runtime.run(turn)).thenReturn(result);

        new OrchestrateStage(runtime).execute(turn).block();

        assertSame(result, turn.getAgentRunResult());
    }

    /** 创建主聊天回合。 */
    private ChatTurnContext turn() {
        return ChatTurnContext.open(
                "goal", "goal", "goal", "chat-1", null, false, null);
    }
}
