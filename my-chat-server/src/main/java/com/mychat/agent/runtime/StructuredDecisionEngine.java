package com.mychat.agent.runtime;

import com.mychat.agent.api.AgentScenarioId;
import com.mychat.agent.api.AgentStepResult;
import com.mychat.agent.trace.AgentTraceEvent;
import com.mychat.agent.trace.AgentTracePublisher;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** 使用 Spring AI Structured Output 生成白名单内决策，并有限自修复。 */
@Component
public class StructuredDecisionEngine implements DecisionEngine {

    private static final int MAX_ATTEMPTS = 2;
    private static final int PAPER_FINAL_OUTPUT_MAX = 200;
    private static final int STEP_OBS_CLIP = 600;

    private final ChatClient chatClient;
    private final AgentTracePublisher tracePublisher;

    /** 注入无工具的工作流 ChatClient。 */
    public StructuredDecisionEngine(
            @Qualifier("agentWorkflowChatClient") ChatClient chatClient,
            AgentTracePublisher tracePublisher) {
        this.chatClient = chatClient;
        this.tracePublisher = tracePublisher;
    }

    /** 生成并验证下一步动作；格式错误时补充错误信息重试一次。 */
    @Override
    public AgentDecision decide(AgentDecisionContext context) {
        String validationError = "";
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            long startedAt = System.currentTimeMillis();
            String traceId = context.request().context().runId() + "-model-"
                    + context.nextStepIndex() + "-" + attempt;
            publishTrace(context, traceId, AgentStepResult.Status.RUNNING,
                    "", "", 0);
            try {
                DecisionPayload payload = requestDecision(context, validationError);
                AgentDecision decision = toDecision(payload);
                validationError = validate(context, decision);
                if (validationError.isBlank()) {
                    publishTrace(context, traceId, AgentStepResult.Status.SUCCEEDED,
                            decision.finish() ? "finish" : decision.actionId(), "",
                            System.currentTimeMillis() - startedAt);
                    return decision;
                }
            } catch (RuntimeException exception) {
                lastFailure = exception;
                validationError = "结构化输出解析失败: " + exception.getMessage();
            }
            publishTrace(context, traceId, AgentStepResult.Status.FAILED,
                    "", validationError, System.currentTimeMillis() - startedAt);
        }
        throw new IllegalStateException("决策输出两次校验失败: " + validationError, lastFailure);
    }

    /** 调用模型生成结构化载荷。 */
    private DecisionPayload requestDecision(AgentDecisionContext context, String validationError) {
        String prompt = """
                你是通用 Agent Planner。只决定下一步，不要自行调用工具。
                可用动作：{actions}
                唯一上下文输入：
                {context}
                已完成步骤：{steps}
                当前第 {stepIndex} 步
                上次校验错误：{validationError}
                {paperRules}

                完成时设置 finish=true，并把完整答复放入 finalOutput；
                否则 finish=false，actionId 必须来自可用动作，instruction 要可独立执行。
                parameters 仅放动作必要参数。
                """;
        return chatClient.prompt()
                .user(user -> user.text(prompt)
                        .param("actions", context.profile().allowedActions().toString())
                        .param("context", modelContext(context))
                        .param("steps", formatSteps(context.completedSteps()))
                        .param("stepIndex", String.valueOf(context.nextStepIndex()))
                        .param("validationError", validationError.isBlank() ? "无" : validationError)
                        .param("paperRules", paperRules(context)))
                .call()
                .entity(DecisionPayload.class);
    }

    /** PAPER 场景：禁止把整批 JSON 塞进 finalOutput，避免 LENGTH 截断。 */
    private String paperRules(AgentDecisionContext context) {
        AgentScenarioId scenarioId = context.profile().scenarioId();
        if (scenarioId != AgentScenarioId.PAPER_RESEARCH
                && scenarioId != AgentScenarioId.PAPER_TRADER
                && scenarioId != AgentScenarioId.PAPER_WAREHOUSE_ANALYST
                && scenarioId != AgentScenarioId.PAPER_TRADE_EVALUATOR) {
            return "";
        }
        String action = switch (scenarioId) {
            case PAPER_RESEARCH -> "paper_research";
            case PAPER_TRADER -> "paper_trade_review";
            case PAPER_TRADE_EVALUATOR -> "paper_trade_evaluate";
            default -> "paper_warehouse_analyze";
        };
        String warehouseExtra = scenarioId == AgentScenarioId.PAPER_WAREHOUSE_ANALYST
                ? """
                4. 票仓分析：updateValueTier 成功后下一步必须 finish=true；禁止重复无增量深搜占满步数。
                """
                : "";
        return """
                【PAPER 强制规则】
                1. 必须先调用动作 %s；批次 JSON / 等级变更只能出现在该动作 observation 或写工具中。
                2. 动作成功后 finish=true，finalOutput 只写短确认（如 OK），不超过 %d 字。
                3. 严禁把 ResearchCardBatch / TradeDecisionBatch / StageEvalBatch 写入 finalOutput。
                %s""".formatted(action, PAPER_FINAL_OUTPUT_MAX, warehouseExtra);
    }

    /** 将模型载荷转换为运行时决策。 */
    private AgentDecision toDecision(DecisionPayload payload) {
        if (payload == null) {
            throw new IllegalArgumentException("模型返回空载荷");
        }
        return new AgentDecision(payload.actionId(), payload.instruction(), payload.parameters(),
                payload.finish(), payload.finalOutput(), payload.reason());
    }

    /** 校验终止答复和动作白名单。 */
    private String validate(AgentDecisionContext context, AgentDecision decision) {
        if (decision.finish()) {
            if (decision.finalOutput().isBlank()) {
                return "finish=true 时 finalOutput 不能为空";
            }
            AgentScenarioId scenarioId = context.profile().scenarioId();
            if ((scenarioId == AgentScenarioId.PAPER_RESEARCH
                    || scenarioId == AgentScenarioId.PAPER_TRADER
                    || scenarioId == AgentScenarioId.PAPER_WAREHOUSE_ANALYST
                    || scenarioId == AgentScenarioId.PAPER_TRADE_EVALUATOR)
                    && decision.finalOutput().length() > PAPER_FINAL_OUTPUT_MAX) {
                return "PAPER 场景 finish 时 finalOutput 不得超过 "
                        + PAPER_FINAL_OUTPUT_MAX
                        + " 字；完整批次必须留在动作观察中，finalOutput 仅写 OK";
            }
            return "";
        }
        if (decision.actionId().isBlank()) {
            return "actionId 不能为空";
        }
        if (!context.profile().allowedActions().contains(decision.actionId())) {
            return "actionId 不在白名单: " + decision.actionId();
        }
        return "";
    }

    /** 压缩步骤为 Planner 可读文本；长观察截断以免挤爆后续决策。 */
    private String formatSteps(List<AgentStepResult> steps) {
        if (steps.isEmpty()) {
            return "无";
        }
        return steps.stream()
                .map(step -> step.index() + ". " + step.actionId() + " [" + step.status()
                        + "] " + clip(step.observation(), STEP_OBS_CLIP)
                        + " " + step.errorMessage())
                .toList()
                .toString();
    }

    private static String clip(String text, int max) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "...(truncated)";
    }

    /** 优先使用 ContextBundle 渲染文本，Demo 请求回退原始 input。 */
    private String modelContext(AgentDecisionContext context) {
        Object rendered = context.request().parameters().get("contextText");
        return rendered != null && !String.valueOf(rendered).isBlank()
                ? String.valueOf(rendered)
                : context.request().input();
    }

    /** 发布不含模型 reasoning 和系统提示的模型 Span。 */
    private void publishTrace(AgentDecisionContext context, String traceId,
                              AgentStepResult.Status status, String output,
                              String error, long elapsedMs) {
        tracePublisher.publish(new AgentTraceEvent(
                context.request().context().runId(),
                traceId,
                null,
                AgentTraceEvent.Type.MODEL,
                status,
                "planner",
                "",
                output,
                List.of(),
                elapsedMs,
                error,
                Map.of("attemptStep", context.nextStepIndex())));
    }

    /** Structured Output 载荷。 */
    public record DecisionPayload(String actionId, String instruction, Map<String, Object> parameters,
                                  boolean finish, String finalOutput, String reason) {
    }
}
