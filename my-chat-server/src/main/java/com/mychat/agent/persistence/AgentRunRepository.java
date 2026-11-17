package com.mychat.agent.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mychat.agent.api.AgentRunContext;
import com.mychat.agent.api.AgentRunRequest;
import com.mychat.agent.api.AgentRunResult;
import com.mychat.agent.api.AgentScenarioId;
import com.mychat.agent.api.AgentScenarioProfile;
import com.mychat.agent.api.AgentStepResult;
import com.mychat.agent.trace.AgentTraceEvent;
import com.mychat.entity.po.AgentApproval;
import com.mychat.entity.po.AgentRun;
import com.mychat.entity.po.AgentRunStep;
import com.mychat.mapper.AgentApprovalMapper;
import com.mychat.mapper.AgentRunMapper;
import com.mychat.mapper.AgentRunStepMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Agent Run/Step/Approval 的持久化边界。 */
@Repository
public class AgentRunRepository {

    private static final int SUMMARY_LIMIT = 4_096;
    private final AgentRunMapper runMapper;
    private final AgentRunStepMapper stepMapper;
    private final AgentApprovalMapper approvalMapper;

    /** 注入三个 Mapper。 */
    public AgentRunRepository(AgentRunMapper runMapper, AgentRunStepMapper stepMapper,
                              AgentApprovalMapper approvalMapper) {
        this.runMapper = runMapper;
        this.stepMapper = stepMapper;
        this.approvalMapper = approvalMapper;
    }

    /** 创建 Run；相同 runId 重入时保持原记录。 */
    @Transactional
    public AgentRun createIfAbsent(AgentRunRequest request, AgentScenarioProfile profile) {
        AgentRun existing = runMapper.selectById(request.context().runId());
        if (existing != null) {
            return existing;
        }
        LocalDateTime now = LocalDateTime.now();
        AgentRun run = new AgentRun();
        run.setId(request.context().runId());
        run.setScenario(profile.scenarioId().name());
        run.setProfileVersion(profile.version());
        run.setBusinessRef(request.context().businessRef());
        run.setConversationId(request.context().conversationId());
        run.setUserId(request.context().userId());
        run.setAsyncJobId(text(request.parameters().get("asyncJobId")));
        run.setIdempotencyKey(text(request.parameters().get("idempotencyKey")));
        run.setStatus(AgentRunResult.Status.PAUSED.name());
        run.setCurrentStep(0);
        run.setCheckpointVersion(0);
        run.setCheckpoint(checkpoint(request, List.of()));
        run.setInputSummary(summary(request.input()));
        run.setInputTokens(0L);
        run.setOutputTokens(0L);
        run.setTotalTokens(0L);
        run.setCreatedAt(now);
        run.setUpdatedAt(now);
        runMapper.insert(run);
        return run;
    }

    /** 将 Run 标记为执行中。 */
    public void markRunning(String runId) {
        AgentRun update = new AgentRun();
        update.setId(runId);
        update.setStatus("RUNNING");
        update.setStartedAt(LocalDateTime.now());
        update.setUpdatedAt(LocalDateTime.now());
        runMapper.updateById(update);
    }

    /** 在动作执行前持久化 RUNNING Span。 */
    public AgentRunStep startStep(String runId, int index, String actionId,
                                  String instruction, String idempotencyKey) {
        String stepId = stepId(runId, index);
        AgentRunStep existing = stepMapper.selectById(stepId);
        if (existing != null) {
            return existing;
        }
        LocalDateTime now = LocalDateTime.now();
        AgentRunStep step = new AgentRunStep();
        step.setId(stepId);
        step.setRunId(runId);
        step.setCheckpointIndex(index);
        step.setStepType("ACTION");
        step.setActionId(actionId);
        step.setDisplayName(actionId);
        step.setStatus(AgentStepResult.Status.RUNNING.name());
        step.setIdempotencyKey(idempotencyKey);
        step.setInputSummary(summary(instruction));
        step.setCitations(List.of());
        step.setMetadata(Map.of());
        step.setInputTokens(0L);
        step.setOutputTokens(0L);
        step.setElapsedMs(0L);
        step.setStartedAt(now);
        step.setCreatedAt(now);
        step.setUpdatedAt(now);
        stepMapper.insert(step);
        return step;
    }

    /** 更新 Step 状态，用于审批暂停和恢复执行。 */
    public void updateStepStatus(String runId, int index, String status) {
        AgentRunStep step = new AgentRunStep();
        step.setId(stepId(runId, index));
        step.setStatus(status);
        step.setUpdatedAt(LocalDateTime.now());
        stepMapper.updateById(step);
    }

    /** 完成 Step 并推进安全 checkpoint。 */
    @Transactional
    public void completeStep(AgentRunRequest request, AgentStepResult result,
                             List<AgentStepResult> completedSteps, long elapsedMs) {
        AgentRunStep step = stepMapper.selectById(stepId(request.context().runId(), result.index()));
        if (step == null) {
            step = startStep(request.context().runId(), result.index(), result.actionId(), "", null);
        }
        step.setStatus(result.status().name());
        step.setOutputSummary(summary(result.observation()));
        step.setCitations(asList(result.metadata().get("citations")));
        step.setMetadata(sanitizeMetadata(result.metadata()));
        step.setElapsedMs(Math.max(0, elapsedMs));
        step.setErrorMessage(summary(result.errorMessage()));
        step.setCompletedAt(LocalDateTime.now());
        step.setUpdatedAt(LocalDateTime.now());
        stepMapper.updateById(step);

        runMapper.advanceCheckpoint(
                request.context().runId(),
                result.index(),
                result.index(),
                checkpoint(request, completedSteps),
                LocalDateTime.now());
    }

    /** 保存 Run 终态和聚合指标。 */
    public void completeRun(String runId, AgentRunResult result) {
        AgentRun run = new AgentRun();
        run.setId(runId);
        run.setStatus(result.status().name());
        run.setOutputSummary(summary(result.output()));
        run.setErrorMessage(summary(result.errorMessage()));
        run.setTotalTokens(longValue(result.metrics().get("tokenUsage")));
        run.setUpdatedAt(LocalDateTime.now());
        if (isTerminal(result.status())) {
            run.setCompletedAt(LocalDateTime.now());
        }
        runMapper.updateById(run);
    }

    /** 读取可恢复 checkpoint。 */
    public AgentCheckpoint loadCheckpoint(String runId) {
        AgentRun run = require(runId);
        Map<String, Object> value = run.getCheckpoint() == null ? Map.of() : run.getCheckpoint();
        AgentRunRequest request = restoreRequest(run, map(value.get("request")));
        List<AgentStepResult> steps = restoreSteps(list(value.get("steps")));
        return new AgentCheckpoint(request, steps);
    }

    /** 按幂等键查找已经开始或完成的动作。 */
    public AgentRunStep findByIdempotencyKey(String runId, String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        return stepMapper.selectOne(new LambdaQueryWrapper<AgentRunStep>()
                .eq(AgentRunStep::getRunId, runId)
                .eq(AgentRunStep::getIdempotencyKey, key)
                .last("LIMIT 1"));
    }

    /** 创建待审批记录；相同步骤只保留一条。 */
    public AgentApproval requestApproval(String runId, int stepIndex, String actionId,
                                         String idempotencyKey, String summary, String userId) {
        AgentApproval existing = approvalMapper.selectOne(new LambdaQueryWrapper<AgentApproval>()
                .eq(AgentApproval::getRunId, runId)
                .eq(AgentApproval::getStepId, stepId(runId, stepIndex))
                .last("LIMIT 1"));
        if (existing != null) {
            return existing;
        }
        LocalDateTime now = LocalDateTime.now();
        AgentApproval approval = new AgentApproval();
        approval.setId(UUID.randomUUID().toString());
        approval.setRunId(runId);
        approval.setStepId(stepId(runId, stepIndex));
        approval.setActionId(actionId);
        approval.setIdempotencyKey(idempotencyKey);
        approval.setStatus("PENDING");
        approval.setRequestSummary(summary(summary));
        approval.setRequestedBy(userId);
        approval.setRequestedAt(now);
        approval.setCreatedAt(now);
        approval.setUpdatedAt(now);
        approvalMapper.insert(approval);
        updateStepStatus(runId, stepIndex, "WAITING_APPROVAL");
        return approval;
    }

    /** 查询步骤审批记录。 */
    public AgentApproval findApproval(String runId, int stepIndex) {
        return approvalMapper.selectOne(new LambdaQueryWrapper<AgentApproval>()
                .eq(AgentApproval::getRunId, runId)
                .eq(AgentApproval::getStepId, stepId(runId, stepIndex))
                .last("LIMIT 1"));
    }

    /** 审批或拒绝待处理动作。 */
    public AgentApproval decideApproval(String approvalId, boolean approved,
                                        String decidedBy, String note) {
        AgentApproval approval = approvalMapper.selectById(approvalId);
        if (approval == null) {
            throw new IllegalArgumentException("Agent Approval 不存在: " + approvalId);
        }
        if (!"PENDING".equals(approval.getStatus())) {
            return approval;
        }
        approval.setStatus(approved ? "APPROVED" : "REJECTED");
        approval.setDecidedBy(decidedBy);
        approval.setDecisionNote(summary(note));
        approval.setDecidedAt(LocalDateTime.now());
        approval.setUpdatedAt(LocalDateTime.now());
        approvalMapper.updateById(approval);
        saveTrace(new AgentTraceEvent(
                approval.getRunId(),
                approval.getStepId() + "-approval",
                approval.getStepId(),
                AgentTraceEvent.Type.APPROVAL,
                approved ? AgentStepResult.Status.SUCCEEDED : AgentStepResult.Status.FAILED,
                approval.getActionId(),
                "",
                approved ? "审批通过" : "审批拒绝",
                List.of(),
                0,
                approved ? null : summary(note),
                Map.of("actionId", approval.getActionId())));
        return approval;
    }

    /** 查询 Run 的审批列表。 */
    public List<AgentApproval> listApprovals(String runId) {
        return approvalMapper.selectList(new LambdaQueryWrapper<AgentApproval>()
                .eq(AgentApproval::getRunId, runId)
                .orderByAsc(AgentApproval::getRequestedAt));
    }

    /** 查询 Run。 */
    public AgentRun require(String runId) {
        AgentRun run = runMapper.selectById(runId);
        if (run == null) {
            throw new IllegalArgumentException("Agent Run 不存在: " + runId);
        }
        return run;
    }

    /** 查询 Run 的有序 Trace。 */
    public List<AgentRunStep> listSteps(String runId) {
        return stepMapper.selectList(new LambdaQueryWrapper<AgentRunStep>()
                .eq(AgentRunStep::getRunId, runId)
                .orderByAsc(AgentRunStep::getSequenceNo));
    }

    /** 保存模型、工具、guardrail、handoff、evaluator 等内部 Span。 */
    public void saveTrace(AgentTraceEvent event) {
        AgentRunStep step = stepMapper.selectById(event.stepId());
        boolean insert = step == null;
        if (insert) {
            step = new AgentRunStep();
            step.setId(event.stepId());
            step.setRunId(event.runId());
            step.setCreatedAt(LocalDateTime.now());
        }
        step.setParentStepId(event.parentStepId());
        step.setStepType(event.type().name());
        step.setDisplayName(event.name());
        step.setToolName(event.type() == AgentTraceEvent.Type.TOOL ? event.name() : null);
        step.setStatus(event.status().name());
        step.setInputSummary(summary(event.inputSummary()));
        step.setOutputSummary(summary(event.outputSummary()));
        step.setCitations(event.citations());
        step.setMetadata(sanitizeMetadata(event.metadata()));
        step.setElapsedMs(Math.max(0, event.elapsedMs()));
        step.setErrorMessage(summary(event.errorMessage()));
        step.setUpdatedAt(LocalDateTime.now());
        if (event.status() == AgentStepResult.Status.RUNNING) {
            step.setStartedAt(LocalDateTime.now());
        } else {
            step.setCompletedAt(LocalDateTime.now());
        }
        if (insert) {
            stepMapper.insert(step);
        } else {
            stepMapper.updateById(step);
        }
    }

    /** 条件查询 Run，供只读后台接口使用。 */
    public List<AgentRun> search(String scenario, String status, String businessRef,
                                 LocalDateTime createdFrom, LocalDateTime createdTo, int limit) {
        LambdaQueryWrapper<AgentRun> query = new LambdaQueryWrapper<AgentRun>()
                .eq(scenario != null && !scenario.isBlank(), AgentRun::getScenario, scenario)
                .eq(status != null && !status.isBlank(), AgentRun::getStatus, status)
                .eq(businessRef != null && !businessRef.isBlank(), AgentRun::getBusinessRef, businessRef)
                .ge(createdFrom != null, AgentRun::getCreatedAt, createdFrom)
                .le(createdTo != null, AgentRun::getCreatedAt, createdTo)
                .orderByDesc(AgentRun::getCreatedAt)
                .last("LIMIT " + Math.max(1, Math.min(limit, 200)));
        return runMapper.selectList(query);
    }

    /** 构造稳定 Step ID。 */
    public static String stepId(String runId, int index) {
        return runId + "-step-" + index;
    }

    /** 判断 Run 是否不可再恢复。 */
    private boolean isTerminal(AgentRunResult.Status status) {
        return status == AgentRunResult.Status.SUCCEEDED
                || status == AgentRunResult.Status.FAILED
                || status == AgentRunResult.Status.CANCELLED
                || status == AgentRunResult.Status.BUDGET_EXHAUSTED;
    }

    /** 构建只含恢复必需信息的 checkpoint。 */
    private Map<String, Object> checkpoint(AgentRunRequest request, List<AgentStepResult> steps) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("runId", request.context().runId());
        context.put("scenarioId", request.context().scenarioId().name());
        context.put("conversationId", request.context().conversationId());
        context.put("userId", request.context().userId());
        context.put("kbId", request.context().kbId());
        context.put("workDir", request.context().workDir());
        context.put("businessRef", request.context().businessRef());
        context.put("metadata", request.context().metadata());
        Map<String, Object> requestMap = new LinkedHashMap<>();
        requestMap.put("input", request.input());
        requestMap.put("context", context);
        requestMap.put("parameters", request.parameters());
        List<Map<String, Object>> stepMaps = steps.stream().map(this::stepMap).toList();
        return Map.of("request", requestMap, "steps", stepMaps);
    }

    /** 将步骤转换为 checkpoint Map。 */
    private Map<String, Object> stepMap(AgentStepResult step) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("index", step.index());
        value.put("actionId", step.actionId());
        value.put("status", step.status().name());
        value.put("observation", summary(step.observation()));
        value.put("errorMessage", summary(step.errorMessage()));
        value.put("metadata", sanitizeMetadata(step.metadata()));
        return value;
    }

    /** 从 checkpoint 恢复请求。 */
    private AgentRunRequest restoreRequest(AgentRun run, Map<String, Object> request) {
        Map<String, Object> context = map(request.get("context"));
        AgentRunContext runContext = new AgentRunContext(
                text(context.getOrDefault("runId", run.getId())),
                AgentScenarioId.valueOf(text(context.getOrDefault("scenarioId", run.getScenario()))),
                text(context.get("conversationId")),
                text(context.get("userId")),
                text(context.get("kbId")),
                text(context.get("workDir")),
                text(context.get("businessRef")),
                stringMap(context.get("metadata")));
        return new AgentRunRequest(text(request.get("input")), runContext, map(request.get("parameters")));
    }

    /** 从 checkpoint 恢复步骤。 */
    private List<AgentStepResult> restoreSteps(List<Object> values) {
        List<AgentStepResult> steps = new ArrayList<>();
        for (Object raw : values) {
            Map<String, Object> value = map(raw);
            steps.add(new AgentStepResult(
                    intValue(value.get("index")),
                    text(value.get("actionId")),
                    AgentStepResult.Status.valueOf(text(value.get("status"))),
                    text(value.get("observation")),
                    text(value.get("errorMessage")),
                    map(value.get("metadata"))));
        }
        return List.copyOf(steps);
    }

    /** 去除可能泄露隐藏提示或原始工具结果的元数据。 */
    private Map<String, Object> sanitizeMetadata(Map<String, Object> metadata) {
        Map<String, Object> safe = new LinkedHashMap<>();
        if (metadata == null) {
            return safe;
        }
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            String key = entry.getKey();
            if (!key.equalsIgnoreCase("reasoning")
                    && !key.equalsIgnoreCase("systemPrompt")
                    && !key.equalsIgnoreCase("rawToolResult")) {
                safe.put(key, entry.getValue());
            }
        }
        return safe;
    }

    /** 截断持久化摘要。 */
    private String summary(Object value) {
        String text = text(value);
        return text.length() <= SUMMARY_LIMIT ? text : text.substring(0, SUMMARY_LIMIT) + "[已截断]";
    }

    /** 转 Map。 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> source ? (Map<String, Object>) source : Map.of();
    }

    /** 转字符串 Map。 */
    private Map<String, String> stringMap(Object value) {
        Map<String, String> result = new LinkedHashMap<>();
        map(value).forEach((key, item) -> result.put(key, text(item)));
        return result;
    }

    /** 转 List。 */
    @SuppressWarnings("unchecked")
    private List<Object> list(Object value) {
        return value instanceof List<?> source ? (List<Object>) source : List.of();
    }

    /** citations 转通用 List。 */
    private List<Object> asList(Object value) {
        return list(value);
    }

    /** 转文本，null 保持空串。 */
    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /** 转整数。 */
    private int intValue(Object value) {
        return value instanceof Number number ? number.intValue() : Integer.parseInt(text(value));
    }

    /** 转长整数。 */
    private long longValue(Object value) {
        return value instanceof Number number ? Math.max(0, number.longValue()) : 0L;
    }
}
