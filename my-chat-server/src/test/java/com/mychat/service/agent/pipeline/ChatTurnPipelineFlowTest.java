package com.mychat.service.agent.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mychat.agent.api.AgentRunResult;
import com.mychat.agent.context.DefaultContextAssembler;
import com.mychat.agent.quality.MainChatQualityPolicy;
import com.mychat.service.agent.MainChatRuntimeService;
import com.mychat.service.agent.OrchestrateDialogueContextService;
import com.mychat.service.agent.context.MainChatContextAssembler;
import com.mychat.service.agent.pipeline.stage.AssembleContextStage;
import com.mychat.service.agent.pipeline.stage.EmitRouteStage;
import com.mychat.service.agent.pipeline.stage.LoadDialogueContextStage;
import com.mychat.service.agent.pipeline.stage.OrchestrateStage;
import com.mychat.service.agent.pipeline.stage.QualityLoopStage;
import com.mychat.service.agent.pipeline.stage.StreamFinalAnswerStage;
import com.mychat.service.agent.quality.AgentEvaluatorOptimizerService;
import com.mychat.service.chat.ChatAssistantTurnService;
import com.mychat.utils.ChatStreamEventWriter;
import com.mychat.utils.WorkspaceUtil;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;

import java.util.List;
import java.util.Map;

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
        MainChatRuntimeService runtime = mock(MainChatRuntimeService.class);
        ChatMemory chatMemory = mock(ChatMemory.class);
        ChatAssistantTurnService turns = mock(ChatAssistantTurnService.class);

        when(dialogue.buildForOrchestrate("chat-1")).thenReturn(null);
        when(runtime.run(any())).thenReturn(new AgentRunResult(
                AgentRunResult.Status.SUCCEEDED, "你好", List.of(), null, Map.of()));

        ChatTurnPipeline pipeline = new ChatTurnPipeline(
                new EmitRouteStage(),
                new LoadDialogueContextStage(dialogue),
                new AssembleContextStage(
                        new MainChatContextAssembler(new DefaultContextAssembler())),
                new OrchestrateStage(runtime),
                new StreamFinalAnswerStage(),
                new QualityLoopStage(
                        mock(AgentEvaluatorOptimizerService.class),
                        mock(WorkspaceUtil.class),
                        new MainChatQualityPolicy(),
                        mock(com.mychat.agent.tool.ChatClientProfileFactory.class),
                        mock(com.mychat.agent.runtime.ScenarioProfileRegistry.class)),
                new ChatTurnFinalizer(new OrchestrateTurnPersistence(chatMemory), turns),
                new ChatStreamEventWriter());

        List<String> lines = pipeline
                .streamOrchestrate("agent-in", "短问", "短问", "chat-1", null, false, null)
                .collectList()
                .block();

        List<String> types = lines.stream().map(this::eventType).toList();
        assertEquals(List.of("route", "text_delta", "done"), types);
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
