# PLAN 01 / Phase 3：持久化、恢复与公开时间线

## 前置条件

Phase 1、Phase 2 已通过验收。

## 目标

为新 Runtime 增加 Run/Step/Approval 持久化、checkpoint、恢复和用户可见进度，同时保持现有主聊天 NDJSON 兼容。

## 范围

1. 实施前核对 Flyway：
   - 优先调用 `mcp-database-server.flyway_status`、`describe_tables`。
   - 当前规划基线为 V8；实施时若已有后续版本则顺延。

2. 新增 Flyway：
   - `agent_run`
   - `agent_run_step`
   - `agent_approval`
   - 必需索引、状态约束、业务关联和幂等键。

3. 实现持久化与恢复：
   - Run/Step Repository。
   - 每个安全边界 checkpoint。
   - 服务重启后的可恢复读取步骤。
   - 写动作按 idempotency key 防重。
   - 与 `async_job` 分工：job 管调度通知，agent_run 管语义执行。

4. 实现内部 Trace：
   - workflow/action
   - model
   - tool
   - guardrail
   - handoff
   - evaluator
   - approval

5. 实现 `PublicProgressAdapter`：
   - 内部 Trace 转换为公开进度。
   - 不输出 reasoning、chain-of-thought、系统提示和完整工具结果。
   - 输出步骤、状态、工具名、脱敏摘要、citation、耗时和错误。

6. 兼容现有 NDJSON：
   - 保留 v1 `route/step/tool_call/tool_result/text_delta/error/done`。
   - 增加可选 `runId/stepId/parentStepId/status/displayName/elapsedMs`。
   - 不发送 `thinking_delta`。

7. 前端时间线升级：
   - 按 stepId 原位更新运行中/完成/失败。
   - 工具作为步骤子项。
   - 默认紧凑展示，详情折叠。
   - 实时流和历史回放使用同一归约器。

## 不做

- 不切换主聊天到新 Runtime。
- 不删除旧时间线字段。
- 不接模拟交易。

## 验收门槛

- Run 中断后只从安全 checkpoint 恢复。
- 同一幂等键不会重复执行写工具。
- NDJSON v1 旧前端兼容测试通过。
- 新前端能显示运行中步骤和工具子项。
- 公开事件不含模型 reasoning、隐藏 system 或敏感工具原文。
- 实时与刷新后的时间线结构一致。
