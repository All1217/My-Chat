# PLAN 01 / Phase 5：多场景验证与架构收尾

## 前置条件

正式主聊天已稳定运行在新 Runtime，旧路径已具备可删除条件。

## 目标

证明新编排不是“只为主聊天重写”，完成多场景复用、遗留清理和后续模拟炒股所需交付。

## 范围

1. 实现 `MARKET_ANALYSIS` Profile：
   - WebResearchCapability。
   - 并行分析。
   - 有明确 rubric 的 evaluator。
   - 不直接依赖 `SearchWorker`。

2. 实现 `ROLEPLAY` Profile：
   - 角色 system。
   - 独立会话 Memory。
   - 默认无 file/search/MCP。
   - 默认关闭质量环。

3. 实现 `INTERVIEWER` Profile：
   - 固定面试阶段 Workflow。
   - 动态追问 Agent。
   - 结构化评分。
   - 评分质量检查和人工复核状态。

4. 清理遗留：
   - 旧 DTO、重复 Workflow、Demo 路由。
   - Worker 具体类旁路调用。
   - `orch-*` JDBC 临时 Memory。
   - 过时文档和时序图。

5. 完善运维：
   - Profile 配置说明。
   - Tool 安全等级规范。
   - Run/Span 排障指南。
   - checkpoint 恢复与 approval Runbook。
   - token、耗时、成功率、工具错误率和预算耗尽指标。

6. 为模拟炒股交付：
   - `PAPER_RESEARCH` / `PAPER_TRADER` Profile 注册能力。
   - 固定 Workflow + 受限 Agent Loop。
   - 类型化输出与 Schema 修复。
   - Tool Policy、Guardrail、Trace、checkpoint、取消、恢复和幂等。
   - 同步与 async_job 适配器。
   - business_ref 关联领域 run。

## 验收门槛

- 三种新 Profile 不修改 AgentRuntime 即可接入。
- Profile 工具隔离测试通过。
- 三种场景使用不同 Memory、Context、QualityPolicy 和 AnswerPolicy。
- 旧编排循环和重复路由代码删除后全量测试通过。
- `PLAN_02_PAPER_TRADE_AI_WORKFLOWS.md` 的前置契约全部具备。
- 完成本阶段后，才开始模拟炒股工作流实施。
