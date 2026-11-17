package com.mychat.apps.papertrade.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mychat.agent.api.AgentRunResult;
import com.mychat.agent.api.AgentScenarioId;
import com.mychat.agent.api.AgentStepResult;
import com.mychat.agent.runtime.ScenarioAgentService;
import com.mychat.agent.runtime.StructuredOutputRepairService;
import org.springframework.stereotype.Component;

import java.util.Map;

/** 将模拟炒股固定领域工作流适配到通用 AgentRuntime。 */
@Component
public class PaperAgentRuntimeAdapter {

    private final ScenarioAgentService scenarioAgentService;
    private final StructuredOutputRepairService repairService;
    private final ObjectMapper objectMapper;

    /** 注入多场景入口和 JSON 编解码器。 */
    public PaperAgentRuntimeAdapter(
            ScenarioAgentService scenarioAgentService,
            StructuredOutputRepairService repairService,
            ObjectMapper objectMapper) {
        this.scenarioAgentService = scenarioAgentService;
        this.repairService = repairService;
        this.objectMapper = objectMapper;
    }

    /** 执行搜集员只读 Agent。 */
    public Outcome research(String input, String businessRef, String asyncJobId) {
        return call(AgentScenarioId.PAPER_RESEARCH, input, businessRef, asyncJobId);
    }

    /** 执行交易员只读建议 Agent。 */
    public Outcome trader(String input, String businessRef, String asyncJobId) {
        return call(AgentScenarioId.PAPER_TRADER, input, businessRef, asyncJobId);
    }

    /** 调用通用 Runtime，并保留领域服务当前需要的兼容结果。 */
    private Outcome call(
            AgentScenarioId scenarioId, String input, String businessRef, String asyncJobId) {
        long started = System.nanoTime();
        Map<String, Object> attributes = asyncJobId == null || asyncJobId.isBlank()
                ? Map.of()
                : Map.of("asyncJobId", asyncJobId);
        AgentRunResult result = scenarioAgentService.run(
                new ScenarioAgentService.ScenarioRunCommand(
                        scenarioId,
                        input,
                        businessRef,
                        null,
                        businessRef,
                        null,
                        "输出必须符合场景 outputType Schema，且证据可追溯。",
                        attributes));
        long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
        if (result.status() != AgentRunResult.Status.SUCCEEDED) {
            throw new IllegalStateException(
                    "模拟炒股 Agent 失败: " + result.status() + " " + result.errorMessage());
        }
        String typed = typedOutput(scenarioId, result.output());
        String observations = result.steps().stream()
                .map(AgentStepResult::observation)
                .filter(value -> value != null && !value.isBlank())
                .reduce("", (left, right) -> left + right + "\n");
        return new Outcome(typed, observations, trace(result, elapsedMs));
    }

    /** 按场景 Schema 解析并必要时修复类型化输出。 */
    private String typedOutput(AgentScenarioId scenarioId, String raw) {
        try {
            return switch (scenarioId) {
                case PAPER_RESEARCH -> objectMapper.writeValueAsString(
                        repairService.parseOrRepair(
                                raw, ResearchCardBatch.class,
                                "ResearchCardBatch.cards[] 含 symbol/name/kept/story/risks/"
                                        + "rejectReason/fitScore/evidenceScore/signalTags/evidenceRefs"));
                case PAPER_TRADER -> objectMapper.writeValueAsString(
                        repairService.parseOrRepair(
                                raw, TradeDecisionBatch.class,
                                "TradeDecisionBatch.decisions[] 含 action/symbol/quantity/"
                                        + "confidence/reasoning/riskFlags/manualReview/evidenceRefs"));
                default -> raw;
            };
        } catch (Exception exception) {
            throw new IllegalStateException("类型化输出修复失败", exception);
        }
    }

    /** 生成领域历史页可保存的脱敏轨迹。 */
    private String trace(AgentRunResult result, long elapsedMs) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("elapsedMs", elapsedMs);
        root.put("status", result.status().name());
        ArrayNode steps = root.putArray("steps");
        for (AgentStepResult step : result.steps()) {
            ObjectNode node = steps.addObject();
            node.put("index", step.index());
            node.put("action", step.actionId());
            node.put("status", step.status().name());
            node.put("observation", clip(step.observation(), 500));
        }
        return root.toString();
    }

    /** 截断领域兼容轨迹。 */
    private String clip(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max);
    }

    /** Agent 兼容结果。 */
    public record Outcome(String answer, String observations, String traceJson) {
    }
}
