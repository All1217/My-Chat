package com.mychat.agent.runtime;

import com.mychat.agent.api.AgentRunRequest;
import com.mychat.agent.api.AgentRunResult;
import com.mychat.agent.api.AgentScenarioProfile;
import com.mychat.agent.quality.AgentQualityPolicyRegistry;
import com.mychat.agent.persistence.AgentCheckpoint;
import com.mychat.agent.persistence.AgentRunRepository;
import com.mychat.entity.po.AgentRun;
import org.springframework.stereotype.Component;

/** 使用场景 Profile 和默认决策器驱动 AgentLoop。 */
@Component
public class DefaultAgentRuntime implements AgentRuntime {

    private final ScenarioProfileRegistry profiles;
    private final AgentLoop loop;
    private final DecisionEngine decisionEngine;
    private final WorkflowRunner workflowRunner;
    private final AgentQualityPolicyRegistry qualityPolicies;
    private final AgentRunRepository repository;
    private final AgentRunControl runControl;

    /** 注入运行时依赖。 */
    public DefaultAgentRuntime(ScenarioProfileRegistry profiles, AgentLoop loop,
                               DecisionEngine decisionEngine, WorkflowRunner workflowRunner,
                               AgentQualityPolicyRegistry qualityPolicies,
                               AgentRunRepository repository, AgentRunControl runControl) {
        this.profiles = profiles;
        this.loop = loop;
        this.decisionEngine = decisionEngine;
        this.workflowRunner = workflowRunner;
        this.qualityPolicies = qualityPolicies;
        this.repository = repository;
        this.runControl = runControl;
    }

    /** 加载场景 Profile 后执行。 */
    @Override
    public AgentRunResult run(AgentRunRequest request) {
        validate(request);
        AgentScenarioProfile profile = profiles.require(request.context().scenarioId());
        AgentRunResult result = workflowRunner.run(
                profile.workflowStrategy(), request, profile);
        return applyQuality(request, profile, result);
    }

    /** 从持久化安全边界继续执行。 */
    @Override
    public AgentRunResult resume(String runId) {
        AgentRun run = repository.require(runId);
        if (isTerminal(run.getStatus())) {
            throw new IllegalStateException("终态 Agent Run 不可恢复: " + run.getStatus());
        }
        AgentCheckpoint checkpoint = repository.loadCheckpoint(runId);
        AgentScenarioProfile profile = profiles.require(checkpoint.request().context().scenarioId());
        AgentRunResult result = loop.run(
                checkpoint.request(), profile, decisionEngine, checkpoint.completedSteps());
        return applyQuality(checkpoint.request(), profile, result);
    }

    /** 写入跨进程取消信号。 */
    @Override
    public void cancel(String runId) {
        runControl.cancel(runId);
    }

    /** 校验持久化运行所需的最小上下文。 */
    private void validate(AgentRunRequest request) {
        if (request.context().runId() == null || request.context().runId().isBlank()) {
            throw new IllegalArgumentException("AgentRunContext.runId 不能为空");
        }
        if (request.context().runId().length() > 48) {
            throw new IllegalArgumentException("AgentRunContext.runId 长度不能超过 48");
        }
        if (request.context().scenarioId() == null) {
            throw new IllegalArgumentException("AgentRunContext.scenarioId 不能为空");
        }
    }

    /** 判断持久化状态是否为不可恢复终态。 */
    private boolean isTerminal(String status) {
        return "SUCCEEDED".equals(status)
                || "FAILED".equals(status)
                || "CANCELLED".equals(status)
                || "BUDGET_EXHAUSTED".equals(status);
    }

    /** 执行 Profile 质量策略，并同步持久化新增质量指标。 */
    private AgentRunResult applyQuality(
            AgentRunRequest request,
            AgentScenarioProfile profile,
            AgentRunResult result) {
        AgentRunResult evaluated = qualityPolicies.require(
                profile.qualityPolicy()).evaluate(request, result);
        if (evaluated != result) {
            repository.completeRun(request.context().runId(), evaluated);
        }
        return evaluated;
    }
}
