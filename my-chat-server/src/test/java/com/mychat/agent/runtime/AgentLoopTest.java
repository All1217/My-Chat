package com.mychat.agent.runtime;

import com.mychat.agent.api.AgentAction;
import com.mychat.agent.api.AgentRunContext;
import com.mychat.agent.api.AgentRunRequest;
import com.mychat.agent.api.AgentRunResult;
import com.mychat.agent.api.AgentScenarioId;
import com.mychat.agent.api.AgentScenarioProfile;
import com.mychat.agent.api.AgentStepResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** AgentLoop 的 Fake DecisionEngine 回归测试。 */
class AgentLoopTest {

    /** 验证失败观察会回灌，Planner 可改选动作并完成。 */
    @Test
    void shouldRecoverFromFailedActionAndFinish() {
        AgentActionHandler failing = handler("failing", false, true);
        AgentActionHandler success = handler("success", false, false);
        AgentLoop loop = loop(List.of(failing, success));
        Queue<AgentDecision> decisions = new ArrayDeque<>(List.of(
                AgentDecision.action("failing", "first", Map.of()),
                AgentDecision.action("success", "fallback", Map.of()),
                AgentDecision.finish("done")));

        AgentRunResult result = loop.run(request(Map.of()), profile(
                Set.of("failing", "success"), 4, 2), context -> decisions.remove());

        assertThat(result.status()).isEqualTo(AgentRunResult.Status.SUCCEEDED);
        assertThat(result.output()).isEqualTo("done");
        assertThat(result.steps()).extracting(AgentStepResult::status)
                .containsExactly(AgentStepResult.Status.FAILED, AgentStepResult.Status.SUCCEEDED);
    }

    /** 验证重复动作会暂停，防止循环空转。 */
    @Test
    void shouldPauseRepeatedDecision() {
        AgentLoop loop = loop(List.of(handler("read", false, false)));
        AgentDecision repeated = AgentDecision.action("read", "same", Map.of());

        AgentRunResult result = loop.run(request(Map.of()), profile(Set.of("read"), 4, 4),
                context -> repeated);

        assertThat(result.status()).isEqualTo(AgentRunResult.Status.PAUSED);
        assertThat(result.errorMessage()).contains("重复动作");
    }

    /** 验证最大步骤预算会形成明确终态。 */
    @Test
    void shouldExhaustStepBudget() {
        AgentLoop loop = loop(List.of(handler("read", false, false)));

        AgentRunResult result = loop.run(request(Map.of()), profile(Set.of("read"), 1, 2),
                context -> AgentDecision.action("read", "step-" + context.nextStepIndex(), Map.of()));

        assertThat(result.status()).isEqualTo(AgentRunResult.Status.BUDGET_EXHAUSTED);
        assertThat(result.steps()).hasSize(1);
    }

    /** 验证取消与写动作审批终态。 */
    @Test
    void shouldExposeCancellationAndApprovalStates() {
        AgentLoop loop = loop(List.of(handler("write", true, false)));
        AgentRunResult cancelled = loop.run(request(Map.of("cancelled", true)),
                profile(Set.of("write"), 2, 1),
                context -> AgentDecision.action("write", "x", Map.of()));
        AgentRunResult approval = loop.run(request(Map.of()),
                profile(Set.of("write"), 2, 1),
                context -> AgentDecision.action("write", "x", Map.of()));

        assertThat(cancelled.status()).isEqualTo(AgentRunResult.Status.CANCELLED);
        assertThat(approval.status()).isEqualTo(AgentRunResult.Status.WAITING_APPROVAL);
    }

    /** 验证恢复只从已完成 checkpoint 的下一步继续。 */
    @Test
    void shouldResumeAfterCompletedCheckpoint() {
        AgentLoop loop = loop(List.of(handler("read", false, false)));
        AgentStepResult completed = new AgentStepResult(
                1, "read", AgentStepResult.Status.SUCCEEDED,
                "checkpoint", null, Map.of("toolCalls", 0));

        AgentRunResult result = loop.run(
                request(Map.of()),
                profile(Set.of("read"), 3, 3),
                context -> {
                    assertThat(context.nextStepIndex()).isEqualTo(2);
                    assertThat(context.completedSteps()).containsExactly(completed);
                    return AgentDecision.finish("resumed");
                },
                List.of(completed));

        assertThat(result.status()).isEqualTo(AgentRunResult.Status.SUCCEEDED);
        assertThat(result.steps()).containsExactly(completed);
    }

    /** 创建待测循环。 */
    private AgentLoop loop(List<AgentActionHandler> handlers) {
        return new AgentLoop(
                new ActionRegistry(handlers),
                new AnswerPolicyRegistry(List.of(new DirectAnswerPolicy())),
                new LoopGuard(),
                List.of(),
                journal(),
                runControl());
    }

    /** 创建不持久化的测试执行日志。 */
    private ActionExecutionJournal journal() {
        return new ActionExecutionJournal() {
            /** 直接放行动作。 */
            @Override
            public Preparation prepare(AgentRunRequest request, AgentDecision decision,
                                       AgentAction action, int stepIndex) {
                return new Preparation("test-key", null, null);
            }

            /** 测试无需保存审批。 */
            @Override
            public void requestApproval(AgentRunRequest request, AgentDecision decision,
                                        AgentAction action, int stepIndex, String idempotencyKey) {
            }

            /** approvedActions 中包含动作时视为批准。 */
            @Override
            public boolean isApproved(AgentRunRequest request, int stepIndex, String actionId) {
                Object values = request.parameters().get("approvedActions");
                return values instanceof Iterable<?> iterable
                        && java.util.stream.StreamSupport.stream(iterable.spliterator(), false)
                        .map(String::valueOf)
                        .anyMatch(actionId::equals);
            }
        };
    }

    /** 创建仅读取请求参数的取消控制器。 */
    private AgentRunControl runControl() {
        return new AgentRunControl() {
            /** 测试不提供外部取消。 */
            @Override
            public boolean isCancelled(String runId) {
                return false;
            }

            /** 测试无需取消持久化。 */
            @Override
            public void cancel(String runId) {
            }
        };
    }

    /** 创建 Fake Action。 */
    private AgentActionHandler handler(String id, boolean write, boolean fail) {
        return new AgentActionHandler() {
            /** 返回 Fake Action 描述。 */
            @Override
            public AgentAction action() {
                return new AgentAction(id, id, "{}", "text", write
                        ? AgentAction.SideEffectLevel.REVERSIBLE_WRITE
                        : AgentAction.SideEffectLevel.READ_ONLY);
            }

            /** 返回 Fake 结果或模拟异常。 */
            @Override
            public AgentStepResult handle(AgentRunRequest request, AgentDecision decision, int stepIndex) {
                if (fail) {
                    throw new IllegalStateException("simulated");
                }
                return new AgentStepResult(stepIndex, id, AgentStepResult.Status.SUCCEEDED,
                        "observation-" + stepIndex, null, Map.of("toolCalls", 1));
            }
        };
    }

    /** 创建测试请求。 */
    private AgentRunRequest request(Map<String, Object> parameters) {
        AgentRunContext context = new AgentRunContext("run", AgentScenarioId.MAIN_CHAT,
                "conversation", "user", null, null, null, Map.of());
        return new AgentRunRequest("goal", context, parameters);
    }

    /** 创建测试 Profile。 */
    private AgentScenarioProfile profile(Set<String> actions, int maxSteps, int maxToolCalls) {
        return new AgentScenarioProfile(AgentScenarioId.MAIN_CHAT, "test", actions, Set.of(),
                maxSteps, maxToolCalls, "text", "direct");
    }
}
