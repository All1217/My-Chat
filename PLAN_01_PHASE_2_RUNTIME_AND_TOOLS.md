# PLAN 01 / Phase 2：通用 Runtime 与工具隔离

## 前置条件

`PLAN_01_PHASE_1_BASELINE_AND_CONTRACTS.md` 已通过验收。

## 目标

实现可独立测试的通用 Agent Runtime、动态动作注册和 Profile 工具白名单，但仍不切换主聊天生产路径。

## 范围

1. 实现运行时：
   - `AgentRuntime`
   - `AgentLoop`
   - `WorkflowRunner`
   - `ScenarioProfileRegistry`
   - `ActionRegistry`
   - `AgentRunListener`
   - `AnswerPolicy`

2. 实现基础策略：
   - `StructuredDecisionEngine`
   - `RoutingStrategy`
   - `ChainStrategy`
   - `OrchestratorWorkersStrategy`
   - `EvaluatorOptimizerStrategy`

3. 实现自主循环约束：
   - 最大步骤、最大工具调用、token、耗时预算。
   - `LoopGuard` 检测重复动作、重复工具和无新增信息。
   - 终态：完成、失败、取消、预算耗尽、等待批准、暂停。
   - Spring AI 结构化输出 Schema 验证与有限自修复。

4. 重构工具装配：
   - `ToolCatalog`
   - `ToolPolicy`
   - `ToolExecutionGuard`
   - `ChatClientProfileFactory`
   - Profile 只能看到白名单工具。

5. 将现有能力包装成 Action Handler：
   - general
   - file
   - search
   - retrieve_kb

6. 知识库只做适配：
   - `retrieve_kb` 继续调用 `KnowledgeRetrievalService`。
   - 保留 catalog/vector、citations、query 优先级和 RAG ChatClient。
   - 不修改入库、切段、embedding、vector_store。

7. 提供只读 Demo/测试入口运行新 Runtime，与旧编排并存。

## 不做

- 不切换 `ChatTurnPipeline`。
- 不删除旧 `AgentOrchestratorService`。
- 不新增 Agent Run 表。
- 不改正式前端时间线。
- 不接模拟交易。

## 验收门槛

- Fake DecisionEngine 可完整测试循环、重试、预算和终止。
- 不修改 Runtime 即可注册新的测试 Profile/Action。
- MAIN_CHAT 测试 Profile 无法看到 papertrade 工具。
- ROLEPLAY 测试 Profile 无法看到 file/search/MCP。
- `retrieve_kb` 新旧路径返回一致 citation 与 scope。
- 工具失败能结构化回灌，并允许 Planner 选择替代动作。
