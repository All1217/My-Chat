package com.mychat.agent.persistence;

import com.mychat.agent.api.AgentAction;
import com.mychat.agent.api.AgentRunRequest;
import com.mychat.agent.api.AgentStepResult;
import com.mychat.agent.runtime.ActionExecutionJournal;
import com.mychat.agent.runtime.AgentDecision;
import com.mychat.agent.trace.AgentTraceEvent;
import com.mychat.entity.po.AgentApproval;
import com.mychat.entity.po.AgentRunStep;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;

/** 使用持久化 Step 占用幂等键，避免写动作在恢复时盲目重放。 */
@Component
public class PersistentActionExecutionJournal implements ActionExecutionJournal {

    private final AgentRunRepository repository;

    /** 注入运行仓库。 */
    public PersistentActionExecutionJournal(AgentRunRepository repository) {
        this.repository = repository;
    }

    /** 只读动作直接放行；写动作先持久化稳定幂等键。 */
    @Override
    public Preparation prepare(AgentRunRequest request, AgentDecision decision,
                               AgentAction action, int stepIndex) {
        if (action.sideEffectLevel() == AgentAction.SideEffectLevel.READ_ONLY) {
            return new Preparation(null, null, null);
        }
        String key = idempotencyKey(request, decision, stepIndex);
        AgentRunStep existing = repository.findByIdempotencyKey(request.context().runId(), key);
        if (existing == null) {
            try {
                repository.startStep(request.context().runId(), stepIndex, action.id(),
                        decision.instruction(), key);
                return new Preparation(key, null, null);
            } catch (DataIntegrityViolationException exception) {
                existing = repository.findByIdempotencyKey(request.context().runId(), key);
            }
        }
        if (existing == null) {
            return new Preparation(key, null, "幂等占用失败");
        }
        if (AgentStepResult.Status.SUCCEEDED.name().equals(existing.getStatus())) {
            Map<String, Object> metadata = new LinkedHashMap<>(
                    existing.getMetadata() == null ? Map.of() : existing.getMetadata());
            metadata.put("replayed", true);
            AgentStepResult replay = new AgentStepResult(stepIndex, action.id(),
                    AgentStepResult.Status.SUCCEEDED, existing.getOutputSummary(), null, metadata);
            return new Preparation(key, replay, null);
        }
        if ("WAITING_APPROVAL".equals(existing.getStatus())
                && isApproved(request, stepIndex, action.id())) {
            repository.updateStepStatus(request.context().runId(), stepIndex, "RUNNING");
            return new Preparation(key, null, null);
        }
        return new Preparation(key, null,
                "相同幂等键的写动作已开始且结果未确认，已阻止重复执行");
    }

    /** 创建审批并把 Step 标记为 WAITING_APPROVAL。 */
    @Override
    public void requestApproval(AgentRunRequest request, AgentDecision decision,
                                AgentAction action, int stepIndex, String idempotencyKey) {
        repository.requestApproval(request.context().runId(), stepIndex, action.id(),
                idempotencyKey, decision.instruction(), request.context().userId());
        String parentId = AgentRunRepository.stepId(request.context().runId(), stepIndex);
        repository.saveTrace(new AgentTraceEvent(
                request.context().runId(),
                parentId + "-approval",
                parentId,
                AgentTraceEvent.Type.APPROVAL,
                AgentStepResult.Status.RUNNING,
                action.id(),
                "",
                "等待人工批准",
                java.util.List.of(),
                0,
                null,
                Map.of("actionId", action.id())));
    }

    /** 支持数据库审批和调用方显式批准列表。 */
    @Override
    public boolean isApproved(AgentRunRequest request, int stepIndex, String actionId) {
        AgentApproval approval = repository.findApproval(request.context().runId(), stepIndex);
        if (approval != null) {
            return "APPROVED".equals(approval.getStatus());
        }
        Object allowed = request.parameters().get("approvedActions");
        return allowed instanceof Iterable<?> values
                && java.util.stream.StreamSupport.stream(values.spliterator(), false)
                .map(String::valueOf)
                .anyMatch(actionId::equals);
    }

    /** 生成跨恢复稳定的写动作幂等键。 */
    private String idempotencyKey(AgentRunRequest request, AgentDecision decision, int stepIndex) {
        Object explicit = decision.parameters().get("idempotencyKey");
        if (explicit != null && !String.valueOf(explicit).isBlank()) {
            return String.valueOf(explicit);
        }
        String source = request.context().runId() + "|" + stepIndex + "|" + decision.actionId();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("生成幂等键失败", exception);
        }
    }
}
