package com.mychat.apps.papertrade.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

/**
 * 领域步骤轻量轨迹，写入 framework_trace，并兼容关联 agent_run。
 * 不替代 agent_run_step；只做历史页可解释摘要。
 */
public class PaperWorkflowTrace {

    private final List<StepRecord> steps = new ArrayList<>();
    private String agentRunId;

    /** 记录一步成功摘要。 */
    public void ok(PaperWorkflowStep step, String summary) {
        steps.add(new StepRecord(step.name(), "SUCCEEDED", summary, null));
    }

    /** 覆盖同名步骤（用于中途进度，避免 UI 多条同名节点）。 */
    public void upsertOk(PaperWorkflowStep step, String summary) {
        String name = step.name();
        steps.removeIf(s -> name.equals(s.step()));
        ok(step, summary);
    }

    /** 记录一步失败。 */
    public void fail(PaperWorkflowStep step, String error) {
        steps.add(new StepRecord(step.name(), "FAILED", null, error));
    }

    /** 绑定本轮 Agent Run ID。 */
    public void bindAgentRun(String agentRunId) {
        this.agentRunId = agentRunId;
    }

    /** 当前关联的 agent_run.id。 */
    public String agentRunId() {
        return agentRunId;
    }

    /** 序列化为兼容 framework_trace 的 JSON。 */
    public String toJson(ObjectMapper mapper) {
        ObjectNode root = mapper.createObjectNode();
        if (agentRunId != null && !agentRunId.isBlank()) {
            root.put("agentRunId", agentRunId);
        }
        ArrayNode arr = root.putArray("domainSteps");
        for (StepRecord step : steps) {
            ObjectNode node = arr.addObject();
            node.put("step", step.step());
            node.put("status", step.status());
            if (step.summary() != null) {
                node.put("summary", clip(step.summary(), 400));
            }
            if (step.error() != null) {
                node.put("error", clip(step.error(), 400));
            }
        }
        return root.toString();
    }

    private static String clip(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }

    private record StepRecord(String step, String status, String summary, String error) {
    }
}
