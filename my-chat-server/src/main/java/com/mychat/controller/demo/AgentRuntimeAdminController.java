package com.mychat.controller.demo;

import com.mychat.agent.api.AgentRunResult;
import com.mychat.agent.persistence.AgentRunRepository;
import com.mychat.agent.runtime.AgentRuntime;
import com.mychat.agent.trace.PublicProgressQueryService;
import com.mychat.common.result.Result;
import com.mychat.entity.dto.AgentApprovalDecisionRequest;
import com.mychat.vo.AgentApprovalVO;
import com.mychat.vo.AgentRunDetailVO;
import com.mychat.vo.AgentRunSummaryVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.List;

/** Phase 3 Agent Run 查询、恢复、取消与审批 Demo API。 */
@RestController
@RequestMapping("/ai/agent/runs")
public class AgentRuntimeAdminController {

    private final AgentRunRepository repository;
    private final PublicProgressQueryService progressService;
    private final AgentRuntime runtime;

    /** 注入持久化与运行时服务。 */
    public AgentRuntimeAdminController(AgentRunRepository repository,
                                       PublicProgressQueryService progressService,
                                       AgentRuntime runtime) {
        this.repository = repository;
        this.progressService = progressService;
        this.runtime = runtime;
    }

    /** 按场景、状态和业务关联查询 Run。 */
    @GetMapping
    public Result<List<AgentRunSummaryVO>> list(
            @RequestParam(required = false) String scenario,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String businessRef,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdTo,
            @RequestParam(defaultValue = "50") int limit) {
        List<AgentRunSummaryVO> runs = repository.search(
                        scenario, status, businessRef, createdFrom, createdTo, limit)
                .stream()
                .map(AgentRunSummaryVO::from)
                .toList();
        return Result.ok(runs);
    }

    /** 查询 Run 的公开时间线和审批状态。 */
    @GetMapping("/{runId}")
    public Result<AgentRunDetailVO> detail(@PathVariable String runId) {
        AgentRunSummaryVO run = AgentRunSummaryVO.from(repository.require(runId));
        List<AgentApprovalVO> approvals = repository.listApprovals(runId).stream()
                .map(AgentApprovalVO::from)
                .toList();
        return Result.ok(new AgentRunDetailVO(run, progressService.list(runId), approvals));
    }

    /** 从最后一个安全 checkpoint 恢复执行。 */
    @PostMapping("/{runId}/resume")
    public Result<AgentRunResult> resume(@PathVariable String runId) {
        return Result.ok(runtime.resume(runId));
    }

    /** 请求取消正在运行或暂停的 Run。 */
    @PostMapping("/{runId}/cancel")
    public Result<Void> cancel(@PathVariable String runId) {
        runtime.cancel(runId);
        return Result.ok();
    }

    /** 审批或拒绝高风险动作。 */
    @PostMapping("/approvals/{approvalId}/decision")
    public Result<AgentApprovalVO> decide(
            @PathVariable String approvalId,
            @RequestBody AgentApprovalDecisionRequest request) {
        return Result.ok(AgentApprovalVO.from(repository.decideApproval(
                approvalId, request.approved(), request.decidedBy(), request.note())));
    }
}
