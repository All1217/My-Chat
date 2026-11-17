package com.mychat.agent.trace;

import com.mychat.agent.api.AgentStepResult;
import com.mychat.agent.api.PublicProgressEvent;
import com.mychat.agent.persistence.AgentRunRepository;
import com.mychat.entity.po.AgentRunStep;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** 从持久化内部 Trace 生成公开时间线。 */
@Service
public class PublicProgressQueryService {

    private final AgentRunRepository repository;
    private final PublicProgressAdapter adapter;

    /** 注入运行仓库和公开适配器。 */
    public PublicProgressQueryService(AgentRunRepository repository, PublicProgressAdapter adapter) {
        this.repository = repository;
        this.adapter = adapter;
    }

    /** 查询指定 Run 的公开事件。 */
    public List<PublicProgressEvent> list(String runId) {
        return repository.listSteps(runId).stream()
                .map(this::toTrace)
                .map(adapter::adapt)
                .toList();
    }

    /** 将持久化 Step 映射为内部 Trace。 */
    private AgentTraceEvent toTrace(AgentRunStep step) {
        Map<String, Object> metadata = step.getMetadata() == null ? Map.of() : step.getMetadata();
        return new AgentTraceEvent(
                step.getRunId(),
                step.getId(),
                step.getParentStepId(),
                AgentTraceEvent.Type.valueOf(step.getStepType()),
                publicStatus(step.getStatus()),
                step.getDisplayName(),
                "",
                step.getOutputSummary(),
                step.getCitations(),
                step.getElapsedMs() == null ? 0 : step.getElapsedMs(),
                step.getErrorMessage(),
                metadata);
    }

    /** 将持久化扩展状态压缩为公开步骤状态。 */
    private AgentStepResult.Status publicStatus(String status) {
        if ("WAITING_APPROVAL".equals(status) || "PENDING".equals(status)) {
            return AgentStepResult.Status.RUNNING;
        }
        if ("CANCELLED".equals(status)) {
            return AgentStepResult.Status.SKIPPED;
        }
        try {
            return AgentStepResult.Status.valueOf(status);
        } catch (Exception exception) {
            return AgentStepResult.Status.FAILED;
        }
    }
}
