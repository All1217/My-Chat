package com.mychat.controller;

import com.mychat.entity.dto.EvaluateOptimizeRequest;
import com.mychat.entity.dto.OrchestrateRequest;
import com.mychat.entity.dto.RouteRequest;
import com.mychat.vo.EvaluateOptimizeResultVO;
import com.mychat.vo.OrchestrateResultVO;
import com.mychat.vo.RouteResultVO;
import com.mychat.common.result.Result;
import com.mychat.service.AgentEvaluatorOptimizerService;
import com.mychat.service.AgentOrchestratorService;
import com.mychat.service.AgentRoutingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent Workflow 调试 API（旁路，不进入主聊天流）。
 * <p>
 * {@code POST /ai/agent/route} — 单次 Routing；
 * {@code POST /ai/agent/orchestrate} — 回合内 Orchestrator-Workers；
 * {@code POST /ai/agent/evaluate-optimize} — 任务内质量环（Evaluator-Optimizer）。
 */
@Slf4j
@RestController
@RequestMapping("/ai/agent")
@RequiredArgsConstructor
public class AgentDemoController {

    private final AgentRoutingService agentRoutingService;
    private final AgentOrchestratorService agentOrchestratorService;
    private final AgentEvaluatorOptimizerService agentEvaluatorOptimizerService;

    /**
     * 路由调试：先分类再分发到 file / kb / search / general。
     * <p>
     * 请求示例：{@code { "input": "列出工作区文件" }}；
     * kb 路由需额外 {@code kbId}。
     */
    @PostMapping("/route")
    public Result<RouteResultVO> route(@RequestBody RouteRequest request) {
        try {
            RouteResultVO vo = agentRoutingService.route(request);
            return Result.ok(vo);
        } catch (IllegalArgumentException e) {
            log.warn("Routing 参数错误: {}", e.getMessage());
            return Result.fail(400, e.getMessage());
        } catch (Exception e) {
            log.error("Routing 执行失败", e);
            return Result.fail("Routing 执行失败: " + e.getMessage());
        }
    }

    /**
     * Orchestrator-Workers 调试：多步 next_action → 专用 Worker，返回 steps + finalAnswer。
     * <p>
     * 请求示例：
     * {@code { "input": "根据知识库总结 X，并搜索补充近况", "kbId": "...", "maxSteps": 6 }}
     */
    @PostMapping("/orchestrate")
    public Result<OrchestrateResultVO> orchestrate(@RequestBody OrchestrateRequest request) {
        try {
            OrchestrateResultVO vo = agentOrchestratorService.orchestrate(request);
            return Result.ok(vo);
        } catch (IllegalArgumentException e) {
            log.warn("Orchestrate 参数错误: {}", e.getMessage());
            return Result.fail(400, e.getMessage());
        } catch (Exception e) {
            log.error("Orchestrate 执行失败", e);
            return Result.fail("Orchestrate 执行失败: " + e.getMessage());
        }
    }

    /**
     * 任务内质量环（Evaluator-Optimizer）：写盘 → 读回 → 评价 → 不合格再改。
     * <p>
     * 用于单次任务中提升产物质量；不是离线 Agent 评测 / Benchmark。
     * 请求示例：
     * {@code { "goal": "...", "path": "notes/x.md", "criteria": "...", "mustContain": ["## A"], "maxIterations": 3 }}
     */
    @PostMapping("/evaluate-optimize")
    public Result<EvaluateOptimizeResultVO> evaluateOptimize(@RequestBody EvaluateOptimizeRequest request) {
        try {
            EvaluateOptimizeResultVO vo = agentEvaluatorOptimizerService.evaluateOptimize(request);
            return Result.ok(vo);
        } catch (IllegalArgumentException e) {
            log.warn("Evaluate-optimize 参数错误: {}", e.getMessage());
            return Result.fail(400, e.getMessage());
        } catch (Exception e) {
            log.error("Evaluate-optimize 执行失败", e);
            return Result.fail("Evaluate-optimize 执行失败: " + e.getMessage());
        }
    }
}
