# PLAN 01 / Phase 1：回归基线与通用契约

## 目标

在不改变线上主聊天行为、不新增数据库迁移的前提下，冻结现有协议并建立新运行时所需的业务无关契约。

## 范围

1. 为当前主聊天建立黄金测试：
   - NDJSON 顺序：`route -> step -> text_delta -> done/error`。
   - 历史 `chat_assistant_turns.parts` 回放。
   - 近期原文 + 远期摘要。
   - KB citations 与 `catalog/vector`。
   - 附件正文进入当前回合。

2. 新增但暂不接管主路径的契约：
   - `AgentScenarioId`
   - `AgentRunRequest` / `AgentRunContext` / `AgentRunResult`
   - `AgentScenarioProfile`
   - `AgentAction` / `AgentStepResult`
   - `ContextBlock` / `ContextBundle` / `ContextBudgetPolicy`
   - `PublicProgressEvent`

3. 实现 `ContextAssembler` 的 shadow 模式：
   - 同时生成新 `ContextBundle`，但模型仍使用旧 Prompt。
   - 记录 fingerprint 与预算报告用于测试，不写生产日志中的正文。
   - 修复最终回答丢失本轮附件的问题前，先补回归测试确认现状。

4. 用 `AgentRunContext.workDir` 建立显式上下文入口；保留 `WorkspaceContext` 作为旧入口适配器，不移除旧逻辑。

## 不做

- 不实现新 Agent 循环。
- 不改 Tool 装配。
- 不改数据库。
- 不改前端时间线展示。
- 不迁移知识库、主聊天或模拟交易。

## 关键文件

- `my-chat-server/src/main/java/com/mychat/service/agent/pipeline/`
- `my-chat-server/src/main/java/com/mychat/service/agent/OrchestrateDialogueContextService.java`
- `my-chat-server/src/main/java/com/mychat/service/agent/ChatUploadEnrichment.java`
- `my-chat-server/src/main/java/com/mychat/common/ChatStreamEvent.java`
- `my-chat-server/src/test/java/com/mychat/service/agent/`

## 验收门槛

- 现有主聊天测试全部通过，NDJSON 无变更。
- 新契约不依赖 Controller、Vue DTO、papertrade PO 或具体 Worker。
- 同一输入产生稳定 Context fingerprint。
- Context Budget 测试覆盖附件、摘要、近期消息、工具观察和超预算裁剪。
- 可单独回滚本阶段新增代码而不影响业务。
