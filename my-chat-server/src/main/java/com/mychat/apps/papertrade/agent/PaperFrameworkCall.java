package com.mychat.apps.papertrade.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mychat.entity.dto.OrchestrateRequest;
import com.mychat.service.agent.AgentOrchestratorService;
import com.mychat.vo.OrchestrateResultVO;
import com.mychat.vo.OrchestrateStepVO;
import org.springframework.stereotype.Component;

import java.util.List;

/** 用现有编排器跑一轮，并记下步骤轨迹。不走聊天 HTTP。 */
@Component
public class PaperFrameworkCall {

    private final AgentOrchestratorService orchestrator;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PaperFrameworkCall(AgentOrchestratorService orchestrator) {
        this.orchestrator = orchestrator;
    }

    /** 同步编排。maxSteps 由编排器再钳制到 1–8。 */
    public Outcome call(String input, int maxSteps) {
        long started = System.nanoTime();
        OrchestrateRequest request = new OrchestrateRequest();
        request.setInput(input);
        request.setMaxSteps(maxSteps);
        OrchestrateResultVO result = orchestrator.orchestrate(request);
        long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
        String answer = result.getFinalAnswer() == null ? "" : result.getFinalAnswer();
        String observations = observations(result.getSteps());
        return new Outcome(answer, observations, trace(result, elapsedMs));
    }

    private String observations(List<OrchestrateStepVO> steps) {
        if (steps == null || steps.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (OrchestrateStepVO step : steps) {
            if (step.getObservation() != null) {
                sb.append(step.getObservation()).append('\n');
            }
        }
        return sb.toString();
    }

    private String trace(OrchestrateResultVO result, long elapsedMs) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("elapsedMs", elapsedMs);
        root.put("finishedReason", result.getFinishedReason());
        ArrayNode steps = root.putArray("steps");
        if (result.getSteps() != null) {
            for (OrchestrateStepVO step : result.getSteps()) {
                ObjectNode n = steps.addObject();
                n.put("index", step.getIndex());
                n.put("action", step.getAction());
                n.put("reasoning", clip(step.getReasoning(), 300));
                n.put("observation", clip(step.getObservation(), 500));
            }
        }
        return root.toString();
    }

    private static String clip(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max);
    }

    /** 编排结果：最终文本、各步观察、轨迹 JSON。 */
    public record Outcome(String answer, String observations, String traceJson) {
    }
}
