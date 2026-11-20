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
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
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

    /** 执行交易员建议 Agent（可写决策/下单工具）。 */
    public Outcome trader(String input, String businessRef, String asyncJobId) {
        return call(AgentScenarioId.PAPER_TRADER, input, businessRef, asyncJobId);
    }

    /** 交易评估员：产出 StageEvalBatch。 */
    public Outcome evaluate(String input, String businessRef, String asyncJobId) {
        return call(AgentScenarioId.PAPER_TRADE_EVALUATOR, input, businessRef, asyncJobId);
    }

    /** 票仓分析员：深搜后调等级；不强制批次 Schema。 */
    public Outcome warehouseAnalyze(String input, String businessRef, String asyncJobId) {
        long started = System.nanoTime();
        Map<String, Object> attributes = asyncJobId == null || asyncJobId.isBlank()
                ? Map.of()
                : Map.of("asyncJobId", asyncJobId);
        AgentRunResult result = scenarioAgentService.run(
                new ScenarioAgentService.ScenarioRunCommand(
                        AgentScenarioId.PAPER_WAREHOUSE_ANALYST,
                        input,
                        businessRef,
                        null,
                        businessRef,
                        null,
                        "第1步完成 updateValueTier 后下一步必须 finish=true；finalOutput 仅短确认。",
                        attributes));
        long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
        String agentRunId = String.valueOf(result.metrics().getOrDefault("runId", ""));
        String observations = joinObservations(result);
        boolean soft = false;
        if (result.status() != AgentRunResult.Status.SUCCEEDED) {
            // 步数耗尽但等级已落库：按软成功，避免拖死整批
            if (tierUpdatedInSteps(result)) {
                soft = true;
            } else {
                throw new IllegalStateException(
                        "票仓分析员失败: " + result.status() + " " + result.errorMessage());
            }
        }
        String answer = StringUtils.hasText(result.output())
                ? result.output()
                : (soft ? "OK（等级已落库，未 finish）" : "OK");
        return new Outcome(answer, observations, trace(result, elapsedMs, agentRunId, soft), agentRunId);
    }

    /** 拼接步骤观察文本。 */
    private static String joinObservations(AgentRunResult result) {
        return result.steps().stream()
                .map(AgentStepResult::observation)
                .filter(value -> value != null && !value.isBlank())
                .reduce("", (left, right) -> left + right + "\n");
    }

    /**
     * 观察中是否已出现 updateValueTier 成功回执。
     * 典型片段：{"ok":true,"symbol":"300308","from":2,"to":2}
     */
    static boolean tierUpdatedInSteps(AgentRunResult result) {
        if (result == null || result.steps() == null) {
            return false;
        }
        for (AgentStepResult step : result.steps()) {
            if (looksLikeTierUpdateOk(step.observation())) {
                return true;
            }
            if (looksLikeTierUpdateOk(step.errorMessage())) {
                return true;
            }
        }
        return looksLikeTierUpdateOk(result.output())
                || looksLikeTierUpdateOk(result.errorMessage());
    }

    /** 启发式识别 updateValueTier 成功 JSON。 */
    static boolean looksLikeTierUpdateOk(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        String compact = text.replace(" ", "");
        if (!(compact.contains("\"ok\":true") || compact.contains("\"ok\":True"))) {
            return false;
        }
        if (!compact.contains("\"symbol\"")) {
            return false;
        }
        return compact.contains("\"to\":1")
                || compact.contains("\"to\":2")
                || compact.contains("\"to\":3")
                || compact.contains("updateValueTier");
    }

    /** 调用通用 Runtime；失败或截断时尽量从观察抢救可落库批次。 */
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
        String agentRunId = String.valueOf(result.metrics().getOrDefault("runId", ""));
        Typed typed = resolveTyped(scenarioId, result);
        String observations = result.steps().stream()
                .map(AgentStepResult::observation)
                .filter(value -> value != null && !value.isBlank())
                .reduce("", (left, right) -> left + right + "\n");
        return new Outcome(
                typed.json(),
                observations,
                trace(result, elapsedMs, agentRunId, typed.partial()),
                agentRunId);
    }

    /** 优先解析正式输出；失败或非批次短文则从步骤观察/半截 JSON 抢救。 */
    private Typed resolveTyped(AgentScenarioId scenarioId, AgentRunResult result) {
        if (result.status() == AgentRunResult.Status.SUCCEEDED
                && StringUtils.hasText(result.output())
                && looksLikeBatch(result.output())) {
            try {
                return new Typed(typedOutput(scenarioId, result.output()), false);
            } catch (RuntimeException ignored) {
                // 回退抢救
            }
        }
        Typed salvaged = salvage(scenarioId, result);
        if (salvaged != null) {
            return salvaged;
        }
        if (result.status() != AgentRunResult.Status.SUCCEEDED) {
            throw new IllegalStateException(
                    "模拟炒股 Agent 失败: " + result.status() + " " + result.errorMessage());
        }
        throw new IllegalStateException("类型化输出修复失败且无法从观察抢救批次");
    }

    /** 从优先动作观察、全部观察与 raw output 抢救完整对象。 */
    private Typed salvage(AgentScenarioId scenarioId, AgentRunResult result) {
        for (String candidate : salvageCandidates(scenarioId, result)) {
            try {
                if (scenarioId == AgentScenarioId.PAPER_RESEARCH) {
                    ResearchCardBatch batch = PartialJsonSalvage.salvageCards(candidate, objectMapper);
                    if (batch != null && !batch.cards().isEmpty()) {
                        return new Typed(objectMapper.writeValueAsString(batch), true);
                    }
                } else if (scenarioId == AgentScenarioId.PAPER_TRADER) {
                    TradeDecisionBatch batch =
                            PartialJsonSalvage.salvageDecisions(candidate, objectMapper);
                    if (batch != null && !batch.decisions().isEmpty()) {
                        return new Typed(objectMapper.writeValueAsString(batch), true);
                    }
                } else if (scenarioId == AgentScenarioId.PAPER_TRADE_EVALUATOR) {
                    StageEvalBatch batch = objectMapper.readValue(candidate, StageEvalBatch.class);
                    if (batch != null && StringUtils.hasText(batch.summaryText())) {
                        return new Typed(objectMapper.writeValueAsString(batch), true);
                    }
                }
            } catch (Exception ignored) {
                // 尝试下一候选
            }
        }
        return null;
    }

    /** 组装抢救候选文本：优先动作 → 任意成功观察 → 运行输出。 */
    private List<String> salvageCandidates(AgentScenarioId scenarioId, AgentRunResult result) {
        String preferred = switch (scenarioId) {
            case PAPER_RESEARCH -> "paper_research";
            case PAPER_TRADE_EVALUATOR -> "paper_trade_evaluate";
            default -> "paper_trade_review";
        };
        List<String> out = new ArrayList<>();
        for (int i = result.steps().size() - 1; i >= 0; i--) {
            AgentStepResult step = result.steps().get(i);
            if (preferred.equals(step.actionId())
                    && step.status() == AgentStepResult.Status.SUCCEEDED
                    && StringUtils.hasText(step.observation())) {
                out.add(step.observation());
            }
        }
        for (int i = result.steps().size() - 1; i >= 0; i--) {
            AgentStepResult step = result.steps().get(i);
            if (step.status() == AgentStepResult.Status.SUCCEEDED
                    && StringUtils.hasText(step.observation())
                    && !out.contains(step.observation())) {
                out.add(step.observation());
            }
        }
        if (StringUtils.hasText(result.output()) && !out.contains(result.output())) {
            out.add(result.output());
        }
        if (StringUtils.hasText(result.errorMessage()) && result.errorMessage().contains("{")) {
            out.add(result.errorMessage());
        }
        return out;
    }

    private static boolean looksLikeBatch(String raw) {
        String text = raw.trim();
        return text.contains("\"cards\"")
                || text.contains("\"decisions\"")
                || text.contains("\"summaryText\"")
                || text.contains("\"strategyAdvice\"")
                || text.startsWith("[")
                || text.startsWith("{");
    }

    /** 按场景 Schema 解析并必要时修复类型化输出。 */
    private String typedOutput(AgentScenarioId scenarioId, String raw) {
        try {
            return switch (scenarioId) {
                case PAPER_RESEARCH -> objectMapper.writeValueAsString(
                        repairService.parseOrRepair(
                                raw, ResearchCardBatch.class,
                                "ResearchCardBatch.cards[] 含 symbol/name/kept/story/risks/"
                                        + "rejectReason/fitScore/evidenceScore/signalTags/evidenceRefs/valueTier；"
                                        + "valueTier 为 1|2|3；evidenceRefs 必须是字符串数组"));
                case PAPER_TRADER -> objectMapper.writeValueAsString(
                        repairService.parseOrRepair(
                                raw, TradeDecisionBatch.class,
                                "TradeDecisionBatch.decisions[] 含 action/symbol/quantity/"
                                        + "confidence/reasoning/riskFlags/manualReview/evidenceRefs；"
                                        + "evidenceRefs 必须是字符串数组"));
                case PAPER_TRADE_EVALUATOR -> objectMapper.writeValueAsString(
                        repairService.parseOrRepair(
                                raw, StageEvalBatch.class,
                                "StageEvalBatch 含 summaryText/strategyAdvice/emailBody；"
                                        + "evidenceRefs 为字符串数组；建议仅针对可编辑策略"));
                default -> raw;
            };
        } catch (Exception exception) {
            throw new IllegalStateException("类型化输出修复失败", exception);
        }
    }

    /** 生成领域历史页可保存的脱敏轨迹。 */
    private String trace(AgentRunResult result, long elapsedMs, String agentRunId, boolean partial) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("elapsedMs", elapsedMs);
        root.put("status", result.status().name());
        root.put("partialSalvage", partial);
        if (agentRunId != null && !agentRunId.isBlank()) {
            root.put("agentRunId", agentRunId);
        }
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

    private record Typed(String json, boolean partial) {
    }

    /** Agent 兼容结果。 */
    public record Outcome(String answer, String observations, String traceJson, String agentRunId) {
    }
}
