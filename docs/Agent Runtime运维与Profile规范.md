# Agent Runtime 运维与 Profile 规范

## Profile 配置

所有场景通过 `AgentScenarioProfile` 注册，不在 `AgentRuntime` 中增加场景分支。

- `allowedActions`：允许 Planner 选择的动作。
- `allowedTools`：场景可见工具组；动作只能再取其子集。
- `workflowStrategy`：`orchestrator-workers`、`chain`、`routing` 或 `evaluator-optimizer`。
- `memoryPolicy`：`main-conversation`、`isolated-conversation` 或 `ephemeral`。
- `contextPolicy`：场景上下文装配策略 ID。
- `qualityPolicy`：`none`、`explicit-rubric`、`score-review` 等质量策略。
- `answerPolicy`：最终输出策略 ID。
- `maxSteps` / `maxToolCalls`：硬预算，不能由 Prompt 覆盖。

当前正式场景：`MAIN_CHAT`、`MARKET_ANALYSIS`、`ROLEPLAY`、`INTERVIEWER`、
`PAPER_RESEARCH`、`PAPER_TRADER`。

## Tool 安全等级

- `READ_ONLY`：查询、检索和无副作用计算，可自动执行。
- `REVERSIBLE_WRITE`：工作区文件等可恢复写入；必须有显式根目录和幂等键。
- `HIGH_RISK_WRITE`：交易、外部发送、不可逆发布或敏感数据修改；必须进入 approval。

Profile 白名单与动作工具子集取交集。模拟炒股 Agent 不注册订单、账户写入、任意 SQL、
file 或通用 MCP。订单执行只允许领域 Workflow 在 Java 风控后调用。

## Run / Span 排障

1. 以 `agent_run.id` 定位 Run，检查 `scenario_id`、`status`、`business_ref` 和错误。
2. 按 `agent_run_step.step_index` 查看动作、观察摘要、耗时和 checkpoint。
3. 从 Trace 区分 MODEL、TOOL、GUARDRAIL、APPROVAL、QUALITY。
4. 公开时间线仅显示脱敏摘要；原始 Prompt、模型 reasoning、密钥和完整工具结果不得公开。
5. `business_ref` 用于关联 `paper_research_run`、`paper_ai_run` 或其他领域轮次。

## Checkpoint、恢复和审批 Runbook

1. 仅恢复非终态 Run；`SUCCEEDED`、`FAILED`、`CANCELLED`、`BUDGET_EXHAUSTED` 不可恢复。
2. 调用恢复前确认最后 checkpoint 已完成步骤和幂等键。
3. `WAITING_APPROVAL` 必须由人工批准对应 `runId + stepIndex + actionId`。
4. 高风险动作执行前写 approval；拒绝或过期不得自动重放。
5. 恢复只从安全边界继续，已完成工具步骤从 journal 回放，不能重复产生副作用。
6. 模拟交易若已有成交，领域轮次不得整体自动重试。

## 指标

Actuator 暴露 `health`、`info`、`metrics`：

- `agent.runtime.runs{scenario,status}`：运行数；可计算成功率。
- `agent.runtime.duration{scenario}`：耗时分布。
- `agent.runtime.tokens{scenario}`：token 用量分布。
- `agent.runtime.tool.errors{scenario,action}`：动作/工具错误数。
- `agent.runtime.budget.exhausted{scenario}`：预算耗尽数。

排障时同时核对数据库 Run/Step 与应用日志，不以单个公开 NDJSON 事件判断内部状态。


## 类型化输出修复

`StructuredOutputRepairService` 用于 `PAPER_RESEARCH` / `PAPER_TRADER` 等类型化场景：

1. 先按目标 Schema 直接解析。
2. 失败时仅修复结构和字段类型一次，不得新增事实。
3. 领域适配器把修复后的 JSON 回传给业务 Workflow，订单执行仍只允许 Java 风控后调用。


## 模拟炒股桥接

`PaperAgentRuntimeAdapter` 调用 `ScenarioAgentService` 时：

- `businessRef` = `paper_research_run` / `paper_ai_run` 的 ID
- `parameters.asyncJobId` 写入 `agent_run.async_job_id`
- 输出经 `StructuredOutputRepairService` 修复为 `ResearchCardBatch` / `TradeDecisionBatch`
