package com.mychat.service.agent.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mychat.service.agent.AgentOrchestratorService;
import com.mychat.service.agent.OrchestrateDialogueContextService;
import com.mychat.service.agent.OrchestrateListener;
import com.mychat.service.agent.pipeline.stage.EmitRouteStage;
import com.mychat.service.agent.pipeline.stage.LoadDialogueContextStage;
import com.mychat.service.agent.pipeline.stage.OrchestrateStage;
import com.mychat.service.agent.pipeline.stage.QualityLoopStage;
import com.mychat.service.agent.pipeline.stage.StreamFinalAnswerStage;
import com.mychat.service.agent.quality.AgentEvaluatorOptimizerService;
import com.mychat.service.chat.ChatAssistantTurnService;
import com.mychat.utils.ChatStreamEventWriter;
import com.mychat.utils.WorkspaceUtil;
import com.mychat.vo.OrchestrateResultVO;
import com.mychat.vo.OrchestrateStepVO;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 管道级回归：NDJSON 类型序、qualityLoop=false 不跑质量环、COMPLETE 双写。
 */
class ChatTurnPipelineFlowTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void qualityLoopOffEmitsRouteStepTextDoneAndPersists() throws Exception {
        OrchestrateDialogueContextService dialogue = mock(OrchestrateDialogueContextService.class);
        AgentOrchestratorService orchestrator = mock(AgentOrchestratorService.class);
        ChatMemory chatMemory = mock(ChatMemory.class);
        ChatAssistantTurnService turns = mock(ChatAssistantTurnService.class);

        when(dialogue.buildForOrchestrate("chat-1")).thenReturn(null);
        when(orchestrator.orchestrate(any(), any())).thenAnswer(inv -> {
            OrchestrateListener listener = inv.getArgument(1);
            OrchestrateStepVO finish = new OrchestrateStepVO(1, "finish", "enough", "", "你好");
            if (listener != null) {
                listener.onStep(finish);
            }
            return new OrchestrateResultVO("你好", "finish", List.of(finish));
        });
        when(orchestrator.streamFinalAnswer(any(), any(), any())).thenReturn(Flux.just("你", "好"));

        ChatTurnPipeline pipeline = new ChatTurnPipeline(
                new EmitRouteStage(),
                new LoadDialogueContextStage(dialogue),
                new OrchestrateStage(orchestrator),
                new StreamFinalAnswerStage(orchestrator),
                new QualityLoopStage(mock(AgentEvaluatorOptimizerService.class), mock(WorkspaceUtil.class)),
                new ChatTurnFinalizer(new OrchestrateTurnPersistence(chatMemory), turns),
                new ChatStreamEventWriter());

        List<String> lines = pipeline
                .streamOrchestrate("agent-in", "短问", "短问", "chat-1", null, false, null)
                .collectList()
                .block();

        List<String> types = lines.stream().map(this::eventType).toList();
        assertEquals(List.of("route", "step", "text_delta", "text_delta", "done"), types);
        assertFalse(lines.stream().anyMatch(l -> l.contains("evaluate_optimize")));

        verify(chatMemory).add(eq("chat-1"), anyList());
        verify(turns, timeout(1000)).saveTurnFromEvents(
                eq("chat-1"), anyString(), anyList(), eq(false));
    }

    private String eventType(String line) {
        try {
            JsonNode node = mapper.readTree(line);
            return node.get("type").asText();
        } catch (Exception e) {
            throw new AssertionError("无法解析 NDJSON: " + line, e);
        }
    }
}
