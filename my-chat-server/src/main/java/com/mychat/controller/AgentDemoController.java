package com.mychat.controller;

import com.mychat.agent.AgentRoutingService;
import com.mychat.agent.dto.RouteRequest;
import com.mychat.agent.dto.RouteResultVO;
import com.mychat.common.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent Workflow 调试 API（旁路，不进入主聊天流）。
 * <p>
 * 阶段 E：Routing — {@code POST /ai/agent/route}
 */
@Slf4j
@RestController
@RequestMapping("/ai/agent")
@RequiredArgsConstructor
public class AgentDemoController {

    private final AgentRoutingService agentRoutingService;

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
}
