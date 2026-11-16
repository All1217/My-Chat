# PLAN 01 / Phase 4：主聊天迁移与质量环

## 前置条件

Phase 1-3 已通过验收，并完成新旧 Runtime 对照测试。

## 目标

将正式主聊天迁移到新 Runtime，保留上下文、知识库、文件、搜索、最终回答和质量环能力，随后下线旧编排内核。

## 范围

1. 注册正式 `MAIN_CHAT` Profile：
   - general/file/search/retrieve_kb。
   - 明确工具白名单、预算、记忆和输出策略。
   - 使用 ContextAssembler 注入附件、近期原文、远期摘要、工作区、KB 和工具观察。

2. 迁移 `ChatTurnPipeline`：
   - `OrchestrateStage` 调用 `AgentRuntime(MAIN_CHAT)`。
   - `StreamFinalAnswerStage` 迁为 AnswerPolicy。
   - `QualityLoopStage` 迁为 Profile 可选 `QualityPolicy`。
   - `ChatTurnFinalizer` 继续负责主会话 Memory 与公开 parts 落库。

3. 质量环策略：
   - 复杂代码、文档、研究任务可触发。
   - 只有明确 rubric、预算充足且结果可改进时运行。
   - 最多 1-2 次生成—评估—修订。
   - 交易等已产生高风险副作用的步骤禁止质量环重放。
   - 时间线只显示“质量检查/修订/通过”，不显示 evaluator reasoning。

4. 上下文切换：
   - 新 ContextBundle 成为唯一模型输入来源。
   - Worker Memory 使用 ephemeral scope。
   - 移除 Runtime 对 `WorkspaceContext` ThreadLocal 的依赖。

5. 知识库切换：
   - 正式 `retrieve_kb` 走统一 Action。
   - citations、catalog/vector 与现有前端保持一致。
   - 最终回答不重复检索。

6. 灰度与清理：
   - 配置开关允许按会话使用新旧 Runtime。
   - 对比成功率、耗时、token、工具错误和最终回答。
   - 达标后删除旧 `AgentOrchestratorService` 循环、重复 Routing Demo 和具体 Worker 旁路。

## 验收门槛

- 主聊天全部回归测试通过。
- 附件在最终回答中不丢失。
- 近期原文、远期摘要和工具观察无重复注入。
- KB citations 实时与历史一致。
- 模型可自主路由、选工具和 finish，同时受预算、Schema、Guardrail、LoopGuard 约束。
- 质量环只在符合 Profile 规则时触发。
- 灰度指标达到旧路径基线后才删除旧实现。
