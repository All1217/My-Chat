package com.mychat.agent.runtime;

import com.mychat.agent.api.AgentAction;
import com.mychat.agent.api.AgentRunRequest;
import com.mychat.agent.api.AgentRunResult;
import com.mychat.agent.api.AgentScenarioProfile;
import com.mychat.agent.api.AgentStepResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 执行 Planner-Action-Observation 循环并统一收敛终态。 */
@Component
public class AgentLoop {

    private final ActionRegistry actions;
    private final AnswerPolicyRegistry answerPolicies;
    private final LoopGuard loopGuard;
    private final List<AgentRunListener> listeners;
    private final ActionExecutionJournal executionJournal;
    private final AgentRunControl runControl;

    /** 注入运行期扩展点。 */
    public AgentLoop(ActionRegistry actions, AnswerPolicyRegistry answerPolicies,
                     LoopGuard loopGuard, List<AgentRunListener> listeners,
                     ActionExecutionJournal executionJournal, AgentRunControl runControl) {
        this.actions = actions;
        this.answerPolicies = answerPolicies;
        this.loopGuard = loopGuard;
        this.listeners = List.copyOf(listeners);
        this.executionJournal = executionJournal;
        this.runControl = runControl;
    }

    /** 在 Profile 预算和白名单内执行循环。 */
    public AgentRunResult run(AgentRunRequest request, AgentScenarioProfile profile,
                              DecisionEngine decisionEngine) {
        return run(request, profile, decisionEngine, List.of());
    }

    /** 从安全 checkpoint 的已完成步骤继续执行。 */
    public AgentRunResult run(AgentRunRequest request, AgentScenarioProfile profile,
                              DecisionEngine decisionEngine, List<AgentStepResult> completedSteps) {
        long startedAt = System.currentTimeMillis();
        List<AgentStepResult> steps = new ArrayList<>(
                completedSteps == null ? List.of() : completedSteps);
        List<AgentDecision> decisions = new ArrayList<>();
        int toolCalls = steps.stream().mapToInt(step -> number(step.metadata().get("toolCalls"))).sum();
        int tokens = steps.stream().mapToInt(step -> number(step.metadata().get("tokenUsage"))).sum();
        notifyRunStarted(request, profile);

        // 1. 每轮先检查外部终止信号和预算。
        for (int stepIndex = steps.size() + 1; stepIndex <= profile.maxSteps(); stepIndex++) {
            AgentRunResult interrupted = checkInterruption(request, steps, startedAt, toolCalls, tokens);
            if (interrupted != null) {
                return complete(request, interrupted);
            }

            // 2. 获取并校验结构化决策。
            AgentDecision decision;
            try {
                decision = decisionEngine.decide(new AgentDecisionContext(
                        request, profile, List.copyOf(steps), stepIndex,
                        System.currentTimeMillis() - startedAt));
            } catch (Exception exception) {
                return complete(request, result(AgentRunResult.Status.FAILED, "", steps,
                        "决策失败: " + exception.getMessage(), startedAt, toolCalls, tokens));
            }
            if (decision == null) {
                return complete(request, result(AgentRunResult.Status.FAILED, "", steps,
                        "决策器返回空结果", startedAt, toolCalls, tokens));
            }
            if (decision.finish()) {
                String output = answerPolicies.require(profile.answerPolicy()).answer(request, decision, steps);
                return complete(request, result(AgentRunResult.Status.SUCCEEDED, output, steps,
                        null, startedAt, toolCalls, tokens));
            }

            String guardReason = loopGuard.detect(decision, decisions, steps);
            if (!guardReason.isBlank()) {
                return complete(request, result(AgentRunResult.Status.PAUSED, "", steps,
                        guardReason, startedAt, toolCalls, tokens));
            }
            decisions.add(decision);

            AgentActionHandler handler;
            try {
                handler = requireAllowedAction(profile, decision.actionId());
            } catch (IllegalArgumentException exception) {
                return complete(request, result(AgentRunResult.Status.FAILED, "", steps,
                        exception.getMessage(), startedAt, toolCalls, tokens));
            }
            ActionExecutionJournal.Preparation preparation =
                    executionJournal.prepare(request, decision, handler.action(), stepIndex);
            if (preparation.replayResult() != null) {
                AgentStepResult replay = preparation.replayResult();
                steps.add(replay);
                notifyStepCompleted(request, replay);
                continue;
            }
            if (!preparation.proceed()) {
                return complete(request, result(AgentRunResult.Status.PAUSED, "", steps,
                        preparation.blockedReason(), startedAt, toolCalls, tokens));
            }
            if (requiresApproval(handler.action(), request, decision, stepIndex)) {
                executionJournal.requestApproval(request, decision, handler.action(),
                        stepIndex, preparation.idempotencyKey());
                return complete(request, result(AgentRunResult.Status.WAITING_APPROVAL, "", steps,
                        "动作等待批准: " + decision.actionId(), startedAt, toolCalls, tokens));
            }

            // 3. 执行动作；失败作为结构化观察回灌，允许 Planner 改选动作。
            notifyStepStarted(request, decision, stepIndex);
            AgentStepResult step = execute(handler, request, decision, stepIndex);
            steps.add(step);
            notifyStepCompleted(request, step);
            toolCalls += number(step.metadata().get("toolCalls"));
            tokens += number(step.metadata().get("tokenUsage"));
            if (toolCalls > profile.maxToolCalls()) {
                return complete(request, result(AgentRunResult.Status.BUDGET_EXHAUSTED, "", steps,
                        "超过最大工具调用预算", startedAt, toolCalls, tokens));
            }
        }
        return complete(request, result(AgentRunResult.Status.BUDGET_EXHAUSTED, "", steps,
                "超过最大步骤预算", startedAt, toolCalls, tokens));
    }

    /** 校验动作已注册且在场景白名单。 */
    private AgentActionHandler requireAllowedAction(AgentScenarioProfile profile, String actionId) {
        if (!profile.allowedActions().contains(actionId)) {
            throw new IllegalArgumentException("场景不允许动作: " + actionId);
        }
        return actions.require(actionId);
    }

    /** 判断写动作是否获得显式批准。 */
    private boolean requiresApproval(AgentAction action, AgentRunRequest request,
                                     AgentDecision decision, int stepIndex) {
        return action.sideEffectLevel() != AgentAction.SideEffectLevel.READ_ONLY
                && !executionJournal.isApproved(request, stepIndex, decision.actionId());
    }

    /** 捕获动作异常并转为失败步骤。 */
    private AgentStepResult execute(AgentActionHandler handler, AgentRunRequest request,
                                    AgentDecision decision, int stepIndex) {
        try {
            AgentStepResult result = handler.handle(request, decision, stepIndex);
            if (result == null) {
                throw new IllegalStateException("动作返回空结果");
            }
            return result;
        } catch (Exception exception) {
            return new AgentStepResult(stepIndex, decision.actionId(), AgentStepResult.Status.FAILED,
                    "", exception.getMessage(), Map.of("recoverable", true));
        }
    }

    /** 检查取消、暂停、耗时和 token 预算。 */
    private AgentRunResult checkInterruption(AgentRunRequest request, List<AgentStepResult> steps,
                                             long startedAt, int toolCalls, int tokens) {
        if (Boolean.TRUE.equals(request.parameters().get("cancelled"))
                || runControl.isCancelled(request.context().runId())) {
            return result(AgentRunResult.Status.CANCELLED, "", steps, "运行已取消",
                    startedAt, toolCalls, tokens);
        }
        if (Boolean.TRUE.equals(request.parameters().get("paused"))) {
            return result(AgentRunResult.Status.PAUSED, "", steps, "运行已暂停",
                    startedAt, toolCalls, tokens);
        }
        long elapsed = System.currentTimeMillis() - startedAt;
        if (elapsed > longNumber(request.parameters().get("maxDurationMs"), Long.MAX_VALUE)) {
            return result(AgentRunResult.Status.BUDGET_EXHAUSTED, "", steps, "超过耗时预算",
                    startedAt, toolCalls, tokens);
        }
        if (tokens > longNumber(request.parameters().get("maxTokens"), Long.MAX_VALUE)) {
            return result(AgentRunResult.Status.BUDGET_EXHAUSTED, "", steps, "超过 token 预算",
                    startedAt, toolCalls, tokens);
        }
        return null;
    }

    /** 构造统一运行结果和指标。 */
    private AgentRunResult result(AgentRunResult.Status status, String output,
                                  List<AgentStepResult> steps, String error, long startedAt,
                                  int toolCalls, int tokens) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("elapsedMs", System.currentTimeMillis() - startedAt);
        metrics.put("stepCount", steps.size());
        metrics.put("toolCalls", toolCalls);
        metrics.put("tokenUsage", tokens);
        return new AgentRunResult(status, output, steps, error, metrics);
    }

    /** 将数值元数据安全转换为整数。 */
    private int number(Object value) {
        return value instanceof Number number ? Math.max(0, number.intValue()) : 0;
    }

    /** 将配置值安全转换为长整数。 */
    private long longNumber(Object value, long fallback) {
        return value instanceof Number number ? Math.max(0, number.longValue()) : fallback;
    }

    /** 通知运行开始。 */
    private void notifyRunStarted(AgentRunRequest request, AgentScenarioProfile profile) {
        listeners.forEach(listener -> listener.onRunStarted(request, profile));
    }

    /** 通知步骤开始。 */
    private void notifyStepStarted(AgentRunRequest request, AgentDecision decision, int index) {
        listeners.forEach(listener -> listener.onStepStarted(request, decision, index));
    }

    /** 通知步骤结束。 */
    private void notifyStepCompleted(AgentRunRequest request, AgentStepResult result) {
        listeners.forEach(listener -> listener.onStepCompleted(request, result));
    }

    /** 通知运行结束并返回结果。 */
    private AgentRunResult complete(AgentRunRequest request, AgentRunResult result) {
        listeners.forEach(listener -> listener.onRunCompleted(request, result));
        return result;
    }
}
