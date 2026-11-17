package com.mychat.agent.persistence;

import com.mychat.agent.api.AgentRunRequest;
import com.mychat.agent.api.AgentRunResult;
import com.mychat.agent.api.AgentScenarioProfile;
import com.mychat.agent.api.AgentStepResult;
import com.mychat.agent.runtime.AgentDecision;
import com.mychat.agent.runtime.AgentRunListener;
import com.mychat.agent.trace.AgentTraceEvent;
import com.mychat.agent.trace.AgentTracePublisher;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 在每个 Action 安全边界持久化 Run、Step 与 checkpoint。 */
@Component
public class PersistentAgentRunListener implements AgentRunListener {

    private final AgentRunRepository repository;
    private final AgentTracePublisher tracePublisher;
    private final Map<String, List<AgentStepResult>> completedByRun = new ConcurrentHashMap<>();
    private final Map<String, Long> stepStartedAt = new ConcurrentHashMap<>();

    /** 注入运行仓库。 */
    public PersistentAgentRunListener(AgentRunRepository repository,
                                      AgentTracePublisher tracePublisher) {
        this.repository = repository;
        this.tracePublisher = tracePublisher;
    }

    /** 创建或恢复 Run，并标记为运行中。 */
    @Override
    public void onRunStarted(AgentRunRequest request, AgentScenarioProfile profile) {
        repository.createIfAbsent(request, profile);
        repository.markRunning(request.context().runId());
        AgentCheckpoint checkpoint = repository.loadCheckpoint(request.context().runId());
        completedByRun.put(request.context().runId(),
                new ArrayList<>(checkpoint.completedSteps()));
    }

    /** 动作执行前保存 RUNNING Span。 */
    @Override
    public void onStepStarted(AgentRunRequest request, AgentDecision decision, int stepIndex) {
        String runId = request.context().runId();
        repository.startStep(runId, stepIndex, decision.actionId(), decision.instruction(), null);
        stepStartedAt.put(key(runId, stepIndex), System.currentTimeMillis());
    }

    /** 动作完成后推进 checkpoint。 */
    @Override
    public void onStepCompleted(AgentRunRequest request, AgentStepResult result) {
        String runId = request.context().runId();
        List<AgentStepResult> completed = completedByRun.computeIfAbsent(
                runId, ignored -> new ArrayList<>());
        synchronized (completed) {
            completed.removeIf(step -> step.index() == result.index());
            completed.add(result);
            completed.sort(java.util.Comparator.comparingInt(AgentStepResult::index));
            long started = stepStartedAt.getOrDefault(key(runId, result.index()),
                    System.currentTimeMillis());
            long elapsed = System.currentTimeMillis() - started;
            repository.completeStep(request, result, List.copyOf(completed), elapsed);
            saveToolTrace(request, result, elapsed);
        }
    }

    /** 保存 Run 终态并释放进程内状态。 */
    @Override
    public void onRunCompleted(AgentRunRequest request, AgentRunResult result) {
        String runId = request.context().runId();
        repository.completeRun(runId, result);
        completedByRun.remove(runId);
        stepStartedAt.keySet().removeIf(key -> key.startsWith(runId + ":"));
    }

    /** 构造进程内计时键。 */
    private String key(String runId, int stepIndex) {
        return runId + ":" + stepIndex;
    }

    /** 将 Action 暴露的工具摘要保存为父步骤子 Span。 */
    private void saveToolTrace(AgentRunRequest request, AgentStepResult result, long elapsedMs) {
        Object toolName = result.metadata().get("toolName");
        if (toolName == null) {
            return;
        }
        String parentId = AgentRunRepository.stepId(request.context().runId(), result.index());
        tracePublisher.publish(new AgentTraceEvent(
                request.context().runId(),
                parentId + "-tool",
                parentId,
                AgentTraceEvent.Type.TOOL,
                result.status(),
                String.valueOf(toolName),
                "",
                result.observation(),
                list(result.metadata().get("citations")),
                elapsedMs,
                result.errorMessage(),
                Map.of(
                        "toolName", String.valueOf(toolName),
                        "kbScope", result.metadata().getOrDefault("kbScope", ""))));
    }

    /** 将 citations 安全转换为通用列表。 */
    private List<Object> list(Object value) {
        if (!(value instanceof List<?> source)) {
            return List.of();
        }
        return new ArrayList<>(source);
    }
}
