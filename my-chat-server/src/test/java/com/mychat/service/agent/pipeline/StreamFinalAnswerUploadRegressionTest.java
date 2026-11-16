package com.mychat.service.agent.pipeline;

import com.mychat.service.agent.AgentOrchestratorService;
import com.mychat.service.agent.pipeline.stage.StreamFinalAnswerStage;
import com.mychat.vo.OrchestrateResultVO;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 冻结 Phase 1 现状：最终流式参数仍是原问，不在本阶段改变生产行为。 */
class StreamFinalAnswerUploadRegressionTest {

    /** agentInput 含附件时，旧最终回答路径仍只传 originalPrompt。 */
    @Test
    void finalStreamKeepsUsingOriginalPrompt() {
        AgentOrchestratorService orchestrator = mock(AgentOrchestratorService.class);
        OrchestrateResultVO result = new OrchestrateResultVO("答复", "finish", List.of());
        when(orchestrator.streamFinalAnswer(eq("原问"), eq(result), eq("历史")))
                .thenReturn(Flux.just("答复"));
        ChatTurnContext ctx = ChatTurnContext.open(
                "附件正文\n用户的问题：\n原问",
                "附件名\n用户的问题：\n原问",
                "原问",
                "chat-1",
                null,
                false,
                null);
        ctx.setDialogueHistory("历史");
        ctx.setOrchestrateResult(result);

        new StreamFinalAnswerStage(orchestrator).execute(ctx).block();

        verify(orchestrator).streamFinalAnswer("原问", result, "历史");
    }
}
