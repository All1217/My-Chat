package com.mychat.agent.trace;

import com.mychat.agent.api.AgentRunRequest;
import com.mychat.agent.api.AgentRunResult;
import com.mychat.agent.api.AgentScenarioProfile;
import com.mychat.agent.api.AgentStepResult;
import com.mychat.agent.runtime.AgentRunListener;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 发布 Agent token、耗时、成功率、工具错误率和预算耗尽指标。 */
@Component
public class AgentRuntimeMetricsListener implements AgentRunListener {

    private final MeterRegistry meterRegistry;
    private final Map<String, Long> startedAt = new ConcurrentHashMap<>();

    /** 注入 Micrometer 指标注册表。 */
    public AgentRuntimeMetricsListener(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /** 记录 Run 开始时间。 */
    @Override
    public void onRunStarted(AgentRunRequest request, AgentScenarioProfile profile) {
        startedAt.put(request.context().runId(), System.nanoTime());
    }

    /** 记录动作失败，供工具错误率计算。 */
    @Override
    public void onStepCompleted(AgentRunRequest request, AgentStepResult result) {
        if (result.status() == AgentStepResult.Status.FAILED) {
            Counter.builder("agent.runtime.tool.errors")
                    .tag("scenario", request.context().scenarioId().name())
                    .tag("action", result.actionId())
                    .register(meterRegistry)
                    .increment();
        }
    }

    /** 记录 Run 终态、耗时、token 与预算耗尽次数。 */
    @Override
    public void onRunCompleted(AgentRunRequest request, AgentRunResult result) {
        String scenario = request.context().scenarioId().name();
        Counter.builder("agent.runtime.runs")
                .tag("scenario", scenario)
                .tag("status", result.status().name())
                .register(meterRegistry)
                .increment();
        long started = startedAt.getOrDefault(
                request.context().runId(), System.nanoTime());
        Timer.builder("agent.runtime.duration")
                .tag("scenario", scenario)
                .register(meterRegistry)
                .record(Duration.ofNanos(Math.max(0L, System.nanoTime() - started)));
        DistributionSummary.builder("agent.runtime.tokens")
                .tag("scenario", scenario)
                .register(meterRegistry)
                .record(number(result.metrics().get("tokenUsage")));
        if (result.status() == AgentRunResult.Status.BUDGET_EXHAUSTED) {
            Counter.builder("agent.runtime.budget.exhausted")
                    .tag("scenario", scenario)
                    .register(meterRegistry)
                    .increment();
        }
        startedAt.remove(request.context().runId());
    }

    /** 安全读取指标数值。 */
    private double number(Object value) {
        return value instanceof Number number ? Math.max(0D, number.doubleValue()) : 0D;
    }
}
