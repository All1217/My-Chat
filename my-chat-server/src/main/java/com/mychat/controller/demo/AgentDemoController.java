package com.mychat.controller.demo;

import com.mychat.entity.dto.EvaluateOptimizeRequest;
import com.mychat.entity.dto.OrchestrateRequest;
import com.mychat.entity.dto.RouteRequest;
import com.mychat.vo.EvaluateOptimizeResultVO;
import com.mychat.vo.OrchestrateResultVO;
import com.mychat.common.result.Result;
import com.mychat.service.agent.AgentOrchestratorService;
import com.mychat.service.agent.demo.AgentRouteDemoStreamService;
import com.mychat.service.agent.quality.AgentEvaluatorOptimizerService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;

/**
 * Agent 调试 API，不进入主聊天产品路径。
 * <p>
 * {@code POST /ai/agent/route} — 单次 Routing，NDJSON（route / tool_* / text_delta / thinking_delta / done）；
 * {@code POST /ai/agent/orchestrate} — 同步 JSON 多步编排（与主路共用 {@link AgentOrchestratorService}）；
 * {@code POST /ai/agent/evaluate-optimize} — 同步 JSON 质量环。
 */
@Slf4j
@RestController
@RequestMapping("/ai/agent")
@RequiredArgsConstructor
public class AgentDemoController {

    private final AgentRouteDemoStreamService agentRouteDemoStreamService;
    private final AgentOrchestratorService agentOrchestratorService;
    private final AgentEvaluatorOptimizerService agentEvaluatorOptimizerService;

    /**
     * 路由调试（覆盖原同步 {@code Result&lt;RouteResultVO&gt;}）：分类后流式分发到
     * file / kb / search / general。
     * <p>
     * 请求示例：{@code { "input": "列出工作区文件", "workDir": "..." }}；
     * kb 路由需额外 {@code kbId}。响应 {@code application/x-ndjson}。
     */
    @PostMapping(value = "/route", produces = "application/x-ndjson;charset=UTF-8")
    public Flux<String> route(@RequestBody RouteRequest request, HttpServletResponse response) {
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/x-ndjson;charset=UTF-8");
        try {
            return agentRouteDemoStreamService.stream(request);
        } catch (IllegalArgumentException e) {
            log.warn("Routing Demo 参数错误: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    /**
     * Orchestrator-Workers 调试：多步 next_action → 专用 Worker，返回 steps + finalAnswer。
     * <p>
     * 请求示例：
     * {@code { "input": "根据知识库总结 X，并搜索补充近况", "kbId": "...", "maxSteps": 6 }}
     */
    @PostMapping(value = "/orchestrate", produces = MediaType.APPLICATION_JSON_VALUE)
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
    @PostMapping(value = "/evaluate-optimize", produces = MediaType.APPLICATION_JSON_VALUE)
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
