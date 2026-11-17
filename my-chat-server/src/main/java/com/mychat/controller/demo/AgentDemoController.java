package com.mychat.controller.demo;

import com.mychat.agent.api.AgentRunResult;
import com.mychat.agent.api.AgentScenarioId;
import com.mychat.agent.runtime.ScenarioAgentService;
import com.mychat.entity.dto.AgentRuntimeDemoRequest;
import com.mychat.entity.dto.EvaluateOptimizeRequest;
import com.mychat.vo.EvaluateOptimizeResultVO;
import com.mychat.common.result.Result;
import com.mychat.service.agent.quality.AgentEvaluatorOptimizerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Agent 调试 API，不进入主聊天产品路径。
 * <p>
 * {@code POST /ai/agent/runtime} — 运行通用 Runtime；
 * {@code POST /ai/agent/evaluate-optimize} — 同步 JSON 质量环。
 */
@Slf4j
@RestController
@RequestMapping("/ai/agent")
@RequiredArgsConstructor
public class AgentDemoController {

    private final AgentEvaluatorOptimizerService agentEvaluatorOptimizerService;
    private final ScenarioAgentService scenarioAgentService;

    /** 运行多场景 Runtime；默认 MAIN_CHAT，可通过 metadata.scenarioId 切换。 */
    @PostMapping(value = "/runtime", produces = MediaType.APPLICATION_JSON_VALUE)
    public Result<AgentRunResult> runtime(@RequestBody AgentRuntimeDemoRequest request) {
        try {
            AgentScenarioId scenarioId = resolveScenario(request);
            AgentRunResult result = scenarioAgentService.run(
                    new ScenarioAgentService.ScenarioRunCommand(
                            scenarioId,
                            request.input(),
                            request.conversationId(),
                            null,
                            null,
                            request.workDir(),
                            null,
                            request.metadata() == null
                                    ? Map.of()
                                    : Map.<String, Object>copyOf(request.metadata())));
            return Result.ok(result);
        } catch (IllegalArgumentException exception) {
            log.warn("Runtime Demo 参数错误: {}", exception.getMessage());
            return Result.fail(400, exception.getMessage());
        } catch (Exception exception) {
            log.error("Runtime Demo 执行失败", exception);
            return Result.fail("Runtime Demo 执行失败: " + exception.getMessage());
        }
    }

    /** 从请求解析场景，缺省为主聊天。 */
    private AgentScenarioId resolveScenario(AgentRuntimeDemoRequest request) {
        Object raw = request.metadata() == null ? null : request.metadata().get("scenarioId");
        if (raw == null || String.valueOf(raw).isBlank()) {
            return AgentScenarioId.MAIN_CHAT;
        }
        return AgentScenarioId.valueOf(String.valueOf(raw).trim().toUpperCase());
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
