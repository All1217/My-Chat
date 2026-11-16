# 行动计划一：通用 Agent 编排升级

> 执行顺序：本计划必须先于 `PLAN_02_PAPER_TRADE_AI_WORKFLOWS.md` 完成。

> 本文件是总体设计，不应一次性实施。实际执行拆为以下五个阶段，每阶段独立测试、部署和验收：
>
> 1. `PLAN_01_PHASE_1_BASELINE_AND_CONTRACTS.md`
> 2. `PLAN_01_PHASE_2_RUNTIME_AND_TOOLS.md`
> 3. `PLAN_01_PHASE_3_DURABILITY_AND_TIMELINE.md`
> 4. `PLAN_01_PHASE_4_MAIN_CHAT_MIGRATION.md`
> 5. `PLAN_01_PHASE_5_GENERALIZATION_AND_CLEANUP.md`
>
> 任一阶段未通过验收时停止后续阶段，不在同一批改动中跨阶段补救。

## 1. 目标

重构现有 Agent 编排，使其成为业务无关、可注册、可观测、可测试、可恢复的通用运行时，供以下场景复用：

- 主聊天：知识库、文件、联网搜索、通用问答。
- 模拟炒股：受领域规则约束的搜集员与交易员工作流。
- 其他股市分析：多源资料检索、并行分析、汇总和质量评估。
- 角色扮演：稳定角色设定、会话记忆和受限工具。
- AI 面试官：阶段化提问、追问、评分、反馈和人工复核。

允许重构现有 `AgentOrchestratorService`、Worker、DTO 和 ChatClient 装配；保留已稳定的 NDJSON 协议、MCP 桥、模型热切换和聊天持久化。以正确性、可解释性和扩展性优先，允许增加模型回合和响应时间。

## 2. 成熟做法与采用原则

参考：

- Spring AI 2.0 `Building Effective Agents`：区分固定代码路径的 Workflow 与模型自主选择动作的 Agent；支持 chain、routing、parallel、orchestrator-workers、evaluator-optimizer。
- Spring AI 2.0 `ToolCallingAdvisor`：由 Advisor 管理工具调用循环。
- Spring AI 2.0 `StructuredOutputValidationAdvisor`：按 JSON Schema 验证并让模型自修复。
- Anthropic `Building effective agents`：优先使用满足需求的最简单模式；不可预知子任务才使用 orchestrator-workers；有清晰评价标准时使用 evaluator-optimizer。
- OpenAI Agents SDK：以 Agent、Tool、Handoff、Guardrail、Session、Trace/Span 作为独立运行时概念。
- LangGraph 的 durable execution 思路：持久化步骤、幂等副作用、可恢复执行与人工介入。

本项目采用以下原则：

1. Workflow 与 Agent 共用运行时，但不混为一个模式。
2. 场景通过 Profile 声明动作、工具、模型、记忆、上限和输出契约。
3. 工具默认不可见；只有 Profile 白名单中的工具可交给模型。
4. 所有模型输出优先使用类型化结构和 Schema 校验。
5. 所有模型调用、工具调用、handoff、guardrail 和业务步骤都产生结构化 Span。
6. 外部读取可有限重试；写操作必须具备幂等键，不能由模型盲目重放。
7. 高风险副作用必须经过领域服务或人工批准，不允许通用 Agent 直接绕过。

## 3. 当前问题

现有架构的主要问题：

- `AgentOrchestratorService` 将动作固定为 `retrieve_kb/file/search/general/finish`，业务无法注册自己的能力。
- `OrchestrateRequest`、Worker 与主聊天语义耦合，模拟交易只能复用整套主聊天编排。
- `ChatClientAssembler.tool()` 暴露的工具过多，场景之间存在误调用与提示 token 噪音。
- `RoutingWorkflow`、`OrchestratorWorkflow`、Demo 路由存在重复分发逻辑。
- `PaperFrameworkCall` 和行情服务直接依赖具体 Orchestrator/Worker，绕过统一策略与观测。
- `WorkspaceContext` 使用 ThreadLocal，不适合作为异步 Agent Runtime 的上下文来源。
- `framework_trace` 是最终 JSON 快照，没有通用 Run/Span、checkpoint、暂停与恢复。
- 缺少编排环、工具权限、guardrail、故障恢复和跨场景的集成测试。

## 4. 目标架构

### 4.1 通用契约层

在 `com.mychat.agent.api` 建立与业务无关的契约：

- `AgentScenarioId`：场景标识，如 `MAIN_CHAT`、`PAPER_RESEARCH`、`PAPER_TRADER`、`MARKET_ANALYSIS`、`ROLEPLAY`、`INTERVIEWER`。
- `AgentRunRequest`：输入、场景、会话、用户、业务关联 ID、运行参数。
- `AgentRunContext`：显式携带 workDir、memory scope、业务元数据和取消信号，取代 ThreadLocal。
- `AgentRunResult`：终态、类型化输出、步骤摘要、token/耗时、错误。
- `AgentAction`：动作 ID、参数 Schema、结果类型和副作用等级。
- `AgentStepResult`：单步结果，不直接依赖主聊天 VO。
- `AgentScenarioProfile`：允许动作、允许工具、模型策略、最大步数、记忆策略、输出 Schema、guardrail、answer policy。

### 4.2 运行时层

在 `com.mychat.agent.runtime` 实现：

- `AgentRuntime`：统一启动、继续、取消和恢复入口。
- `AgentLoop`：执行“规划/动作/观察/结束”循环。
- `WorkflowRunner`：执行预定义 chain、routing、parallel、orchestrator-workers、evaluator-optimizer。
- `ActionRegistry`：`actionId -> AgentActionHandler`，业务通过 Bean 注册，不修改核心 switch。
- `ScenarioProfileRegistry`：按场景解析 Profile，并在启动时校验动作和工具引用。
- `AgentRunListener`：发出 run/step/model/tool/guardrail/handoff 生命周期事件。
- `AnswerPolicy`：直接返回、最终汇总、流式汇总等可替换策略，不再强制二次 LLM。

`AgentLoop` 和 `WorkflowRunner` 共用 Run、Span、Profile、Tool Policy 与观测设施；业务可以选择固定工作流或自主 Agent，而不是复制基础设施。

### 4.3 决策与工作流策略

在 `com.mychat.agent.strategy` 提供：

- `StructuredDecisionEngine`：替代现有内嵌 `OrchestratorWorkflow`，使用 Spring AI 结构化输出和 Schema 校验。
- `RoutingStrategy`：单步分类后分发。
- `ChainStrategy`：固定顺序步骤。
- `ParallelStrategy`：独立任务并行执行后聚合。
- `OrchestratorWorkersStrategy`：动态拆分不可预知子任务。
- `EvaluatorOptimizerStrategy`：仅在 Profile 提供明确评分标准时启用。

每个策略都只面向通用契约，不直接依赖 HTTP、聊天气泡或具体业务 PO。

### 4.4 工具与能力层

重构 `ChatClientAssembler`：

- `ChatClientProfileFactory` 按 Profile 装配 ChatClient，而不是固定 `tool/rag/workflow` 三个全量客户端。
- 使用 Spring AI 2.0 `ToolCallingAdvisor` 管理工具循环。
- `ToolCatalog` 汇总本地 `@Tool`、MCP ToolCallback 和领域能力。
- `ToolPolicy` 根据场景、用户、运行阶段和副作用等级生成最小工具白名单。
- `ToolExecutionGuard` 做参数校验、超时、结果大小限制、敏感字段清理和幂等检查。
- 将 `SearchWorker` 的底层能力提取为 `WebResearchCapability`，行情模块依赖能力接口而不是 Worker。

工具副作用分级：

- `READ_ONLY`：查询、检索、行情、知识库。
- `REVERSIBLE_WRITE`：可安全重试或覆盖的写入。
- `HIGH_RISK_WRITE`：交易、删除、对外发送；必须经领域服务和 approval policy。

### 4.5 Guardrail、Handoff 与记忆

- `InputGuardrail`：场景适配、注入攻击、非法范围、上下文尺寸。
- `OutputGuardrail`：Schema、事实引用、业务约束、敏感数据。
- `ToolGuardrail`：工具权限、副作用等级、参数和幂等键。
- `HandoffPolicy`：仅 Profile 明确配置时才能移交专业 Agent，并保留父子 Span。
- `MemoryPolicy`：区分无记忆、窗口记忆、摘要记忆和业务会话记忆；角色扮演与面试官使用独立 scope。

## 5. 持久化与 Flyway

先梳理 V1-V8 和现有 `async_job`，新增下一顺序迁移（实施时按仓库最新版本确定编号），建立通用运行记录：

- `agent_run`：run ID、scenario、profile version、business ref、session、status、current step、input/output 摘要、checkpoint、模型、token、错误和时间。
- `agent_run_step`：run ID、序号、parent step、类型、action/tool、status、输入输出摘要、耗时、token 和错误。
- `agent_approval`：高风险动作的请求、状态、审批人、决定和时间。

约束：

- `async_job` 继续负责异步调度和通知；`agent_run` 负责 Agent 语义与恢复。
- 工具原始敏感结果不入库，只保存脱敏摘要和引用。
- 每个可重试副作用步骤保存稳定 `idempotency_key`。
- 每步完成后写 checkpoint；恢复只从安全边界继续。

## 6. 实施阶段

### 阶段 A：建立回归基线

- 为主聊天 NDJSON `step/text_delta/done/error` 建立黄金测试。
- 补齐现有 orchestrator、routing、四类 Worker、MCP 工具选择和 ChatClient 装配测试。
- 固定当前公开 API 行为，重构期间保持前端可用。

### 阶段 B：引入新契约和注册表

- 新增 `agent.api`、`ScenarioProfileRegistry`、`ActionRegistry`。
- 将现有四类 Worker 包装成 Action Handler。
- Profile 启动校验：动作、工具、输出类型和最大步骤必须合法。

### 阶段 C：实现新运行时

- 实现 `AgentRuntime`、`AgentLoop`、Workflow 策略和生命周期 Listener。
- 使用 `StructuredOutputValidationAdvisor` 替换手工 JSON 截取与弱解析。
- 增加预算限制：最大步骤、最大工具调用、最大 token、最长运行时间。
- 终止条件包括完成、失败、取消、预算耗尽、等待批准和可恢复暂停。

### 阶段 D：工具隔离与 ChatClient Profile

- 将全量工具装配改成场景最小白名单。
- MCP 通过 `ToolCatalog` 接入，并保留现有容错桥。
- 移除 Worker 对 ThreadLocal workspace 的依赖，全部使用 `AgentRunContext`。
- 行情服务改依赖 `WebResearchCapability`。

### 阶段 E：持久化、恢复与观测

- 增加 Flyway 迁移、Run/Step/Approval Mapper 和 Repository。
- 将模型、工具、guardrail、handoff、业务步骤写为 Span。
- 接入现有 `async_job` 和 SSE，支持取消、失败重试和服务重启恢复。
- 增加后台只读查询 API，按 scenario/status/time/business ref 检索。

### 阶段 F：迁移主聊天

- 保留 `ChatTurnPipeline` 与 NDJSON 传输层。
- `OrchestrateStage` 改调 `AgentRuntime(MAIN_CHAT)`。
- `StreamFinalAnswerStage` 和 `QualityLoopStage` 变为 Profile 的可选 Answer/Postprocess Policy。
- 对比新旧输出后删除旧 `AgentOrchestratorService` 内核和重复 Demo 路由。

### 阶段 G：证明通用性

至少实现三种差异明显的 Profile：

- `MARKET_ANALYSIS`：search + parallel analysis + evaluator。
- `ROLEPLAY`：角色 system + memory，无 file/search/MCP。
- `INTERVIEWER`：固定阶段 Workflow + 动态追问 + 结构化评分 + 人工复核。

三者不得新增新的编排循环，只能注册 Profile、Action、Tool 和 Guardrail。

### 阶段 H：清理与文档

- 删除旧 DTO、重复 Workflow、具体 Worker 旁路调用和过时 Demo。
- 更新 `docs/资源/agents-更复杂的编排架构.md`、架构图和时序图。
- 提供新增场景的最小模板、工具安全规范、trace 排障指南和恢复 Runbook。

## 7. 验收标准

- 新场景仅通过注册 Profile/Action/Tool 即可接入，不修改 `AgentRuntime`。
- 不同 Profile 只能看到自己的工具；模拟交易写工具不会暴露给主聊天或角色扮演。
- 所有模型输出可进行 Schema 验证和有限自修复。
- 每个 Run 可看到模型、工具、guardrail、handoff 和业务步骤层级 Trace。
- 服务重启后能恢复安全步骤；写操作不会重复执行。
- 主聊天 NDJSON 对前端保持兼容。
- 市场分析、角色扮演、面试官三个 Profile 的集成测试通过。
- 关键路径具备 token、耗时、成功率、工具错误率和预算耗尽指标。

## 8. 对后续模拟炒股计划的交付契约

计划二开始前，通用编排必须提供：

- 可注册 `PAPER_RESEARCH`、`PAPER_TRADER` Profile。
- 固定 Workflow 与受限 Agent Loop 两种执行模式。
- 场景工具白名单和只读/高风险副作用策略。
- 类型化输出与 Schema 自动校验。
- Run/Step Trace、checkpoint、取消、恢复和幂等支持。
- 同步调用与 `async_job` 异步调用适配器。
- 业务关联 ID，使 `agent_run` 可关联 `paper_research_run` / `paper_ai_run`。

## 9. 上下文注入方案

### 9.1 核心原则

新编排不允许各 Worker 自行拼接大段字符串。每一次模型调用前都通过统一 `ContextAssembler` 生成不可变 `ContextBundle`，并记录上下文预算报告。

上下文分成明确槽位：

- `system.base`：场景基础系统提示词。
- `system.role`：角色扮演、面试官、搜集员、交易员等角色约束。
- `system.policy`：工具权限、输出 Schema、guardrail 和禁止行为。
- `user.goal`：用户原始问题，不被摘要替代。
- `upload.turn`：本轮 txt/md/pdf 等文本附件正文。
- `memory.summary`：更久远历史的滚动摘要。
- `memory.recent`：最近若干轮 USER/ASSISTANT 原文。
- `workspace.skeleton`：工作目录骨架和路径规则。
- `knowledge.context`：知识库检索材料及 citation。
- `tool.observations`：本轮工具结果的脱敏摘要。
- `workflow.state`：当前工作流阶段、已完成动作和结构化中间产物。

`ContextBundle` 至少包含：

- 有序 `systemBlocks` 与 `userBlocks`。
- `ToolPolicySnapshot`，明确本次模型调用可见的工具。
- `ContextBudgetReport`，记录各槽位原始大小、保留大小、截断原因。
- `fingerprint`，用于回归测试和 Trace 对比。

### 9.2 上下文优先级与预算

使用模型上下文窗口感知的 `ContextBudgetPolicy`，不再只依赖分散的字符上限：

1. 永不删除：当前用户目标、关键系统安全规则、输出 Schema、当前工作流状态。
2. 高优先级：最近对话、业务关键事实、当前工具观察、有效知识库引用。
3. 中优先级：远期摘要、附件摘要、工作区骨架。
4. 低优先级：重复材料、早期工具全文、低相关检索结果。

预算按 Profile 与模型配置，预留模型输出、工具回合和结构化修复所需 token。裁剪顺序必须稳定且可测试，不能简单从字符串尾部截断。

### 9.3 文本文件输入

保留现有 `ChatUploadEnrichment` 的文件解析能力，但改为产生 `ContextBlock`：

- 本轮 `DECIDE`、相关 Worker 和最终回答都能读取 `upload.turn`，修复当前最终流式回答可能丢失附件正文的问题。
- 默认只把文件名和文件摘要写入长期记忆，不把全文写入 `spring_ai_chat_memory`。
- 用户要求跨轮持续引用时，提供显式“加入知识库”或“保存为会话资产”，不依赖模型暗中记住全文。
- 文件内容带来源 ID、文件名和截断标记，方便 citation 和注入攻击 guardrail。

### 9.4 系统提示词与用户提示词

- `AgentScenarioProfile` 提供基础 system 模板。
- 运行时注入工具策略、输出 Schema 和预算，不允许业务代码复制公共安全提示。
- 用户原始问题作为独立槽位保留；编排器生成的子任务不能覆盖原始目标。
- Prompt 模板版本进入 `agent_run.profile_version` 和 model span，便于回归。

### 9.5 历史对话

保留现有“近期原文 + 远期滚动摘要”模式，继续复用：

- `spring_ai_chat_memory`：近期 USER/ASSISTANT 原文。
- `chat_session_summary`：远期摘要与 `covered_until_sequence_id`。

改造点：

- `MemoryPolicy` 统一决定读取条数、摘要触发、摘要模型和写入时机。
- 主会话只在回合成功收尾时写一次；Worker 使用 `EPHEMERAL(runId, stepId)` 内存，不再产生 `orch-*` JDBC 孤立会话。
- 工具原始 transcript 默认不进入长期对话，只把经过策略筛选的结论和 citation 纳入本轮 observation。
- 角色扮演和面试官使用独立 memory scope，避免与主聊天串话。

### 9.6 工具调用产生的额外上下文

由 `ToolCallingAdvisor` 负责单个模型调用内的工具回路；通用运行时负责跨步骤上下文：

- 工具完整结果只在当前 tool loop 中使用。
- `ToolObservationReducer` 将结果转换成有来源、受大小限制的结构化 observation。
- observation 可进入下一次规划，但原始敏感结果不进入长期 Memory 或公开时间线。
- 相同 citation/材料按稳定 ID 去重。
- 高风险写工具只返回执行凭据与结果摘要，不把凭据、密钥、SQL 或完整账户数据重新注入模型。

### 9.7 取代 ThreadLocal

`AgentRunContext` 显式携带 `workDir`、`kbId`、用户、会话、业务关联和取消信号。`WorkspaceContext` 只保留为旧 HTTP 入口适配器，在进入 `AgentRuntime` 时复制后即不再使用。

## 10. 知识库业务线与新编排

### 10.1 保留现有知识库数据面

不重写以下成熟链路：

- `KnowledgeBaseController` 管理 API。
- `DocumentIngestService` 与 `kb_ingest` 异步任务。
- `DocumentService` 文档解析与切段。
- `ChunkSummaryService` 摘要。
- `DocumentChunkService` 对 `document_chunk` / `vector_store` 的一致写入。
- `EmbeddingService` 与既有 1536 维向量、segment ID 规则。
- `KnowledgeRetrievalService` 的向量检索、catalog 兜底和 citation 生成。

新编排只替换控制面，不建立第二套 RAG。

### 10.2 稳定端口

将现有能力通过稳定接口暴露：

- `KbRetrievalPort.retrieve(kbId, query, scope)`：返回 `RagContext`、命中块和 citations。
- `KbGenerationPort.answer(context, question)`：使用知识库专用 ChatClient 生成受材料约束的回答。
- `SessionKbResolver.resolve(chatId, requestKbId)`：继续采用请求优先、会话绑定兜底。

`KbWorker` 迁为 `retrieve_kb` Action Handler，内部继续调用 `KnowledgeRetrievalService`，不直接访问 VectorStore。

### 10.3 Profile 与检索自主性

- 只有声明 `retrieve_kb` 的 Profile 才能看到知识库能力。
- 会话 `kbId` 决定“在哪个库检索”；Agent 决策 `catalog/vector` 决定“如何检索”。
- 无 `kbId` 时动作返回明确的软失败 observation，避免 Agent 无意义重复调用。
- 检索 query 保留“原始用户目标优先、子任务 instruction 补充”的当前语义。
- 最终回答阶段不二次检索，只汇总已记录的检索 observation，防止 citation 漂移。

### 10.4 Citation 契约

知识库 Action 输出结构化：

- `scope`：`catalog` 或 `vector`。
- `content`：受统一预算限制的上下文。
- `citations`：文档、片段、文件名、摘要和稳定引用 ID。
- `truncated`：是否因预算裁剪。

citations 同时进入内部 Agent Step、公开 NDJSON 和最终回答引用组件；不依赖从模型文本中反向解析 URL。

## 11. 前端时间线与新编排

### 11.1 内部 Trace 与公开进度分离

`AgentRunListener` 产生完整内部事件，`PublicProgressAdapter` 只输出用户可见进度：

- 内部 Trace：模型调用、工具调用、guardrail、handoff、token、耗时、错误和父子 Span。
- 公开时间线：步骤名称、状态、工具名称、脱敏结果摘要、引用和错误。
- 模型 reasoning、chain-of-thought、隐藏系统提示、完整工具结果不进入公开事件。

### 11.2 保留 NDJSON 兼容

主聊天继续使用 `application/x-ndjson`，不改为 Job SSE。现有 `ChatStreamEvent` v1 作为兼容适配层：

- `route`：只显示场景或模式，如“正在规划任务”，不输出分类器 reasoning。
- `step`：显示 action、序号、`RUNNING/SUCCEEDED/FAILED` 和公开摘要。
- `tool_call`：显示“正在调用知识库/搜索/文件工具”及安全参数摘要。
- `tool_result`：显示成功/失败、耗时和截断后的公开摘要。
- `text_delta`：最终回答。
- `error` / `done`：保持现有语义。
- 不发送 `thinking_delta`；前端不显示历史 `thinking` 区块作为 Agent 进度。

新增字段保持可选，旧前端仍能消费：

- `runId`、`stepId`、`parentStepId`。
- `status`。
- `displayName`。
- `visibility=public`。
- `startedAt`、`elapsedMs`。

### 11.3 时间线状态

前端不再只在步骤完成后追加一行，而是按 `stepId` 原位更新：

1. 收到开始事件：展示执行中。
2. 收到工具事件：在步骤下显示子项。
3. 收到完成事件：改为完成并展示耗时、citation 和公开摘要。
4. 收到失败事件：标记失败并显示可操作错误。
5. 页面刷新：从 `chat_assistant_turns.parts` 或 Agent Run 查询接口恢复同一结构。

`AgentActivityTimeline.vue` 保持紧凑布局，默认只显示步骤和状态；instruction、observation、citation 放在折叠区，不展示模型思考。

### 11.4 持久化关联

- `agent_run_step` 是内部权威 Trace。
- `chat_assistant_turns.parts` 继续保存面向聊天 UI 的公开快照，避免历史页面每次联查全部 Span。
- 回合结束由同一个 `PublicProgressAdapter` 归约实时事件和历史 parts，修复当前实时/刷新后的 step 内容不一致。

## 12. 自主路由、工具选择与循环结束的增强

新编排保留并增强旧能力，不退化为固定路由：

### 12.1 自主路由增强

- 旧架构：LLM 在硬编码 action 列表中选择。
- 新架构：LLM 在当前 Profile 的动态 `ActionRegistry` 中选择，动作带参数 Schema、成本、副作用、前置条件和结果类型。
- 支持单步 routing、动态 orchestrator-workers、handoff 到专业 Agent。
- Planner 在每步都能看到结构化工作流状态、预算和失败记录，而非拼接的 observation 文本。

### 12.2 自主工具选择增强

- 旧架构：Worker 使用装有大量工具的 `toolChatClient`，工具边界粗。
- 新架构：Profile、Action 和运行阶段共同计算最小工具白名单。
- 模型仍可通过 `ToolCallingAdvisor` 自主多轮选工具，但受最大调用数、token、时间、参数 Schema 和副作用 guardrail 限制。
- 工具失败会以结构化 observation 回灌；Planner 可以改用替代工具，而不是只得到一段错误文本。

### 12.3 自主结束增强

- 模型仍可输出 `finish`。
- 运行时增加确定性终止校验：目标完成、必需输出存在、Schema 合法、guardrail 通过、预算未超限。
- 支持 `WAITING_APPROVAL`、`PAUSED`、`CANCELLED`、`BUDGET_EXHAUSTED`，不再只有 finish/max_steps/异常。
- 当模型循环、重复工具或无新增信息时，`LoopGuard` 强制终止或进入 evaluator。
- evaluator 只判断可验证质量标准，不读取或展示 chain-of-thought。

### 12.4 规划、执行、评估解耦

采用：

- `DecisionEngine`：下一步做什么。
- `ActionHandler`：执行动作。
- `OutputGuardrail`：结果是否合法。
- `Evaluator`：是否达到场景质量标准。
- `AnswerPolicy`：如何向用户输出。

因此可在不修改 Runtime 的情况下，为模拟交易、市场分析、角色扮演和 AI 面试官配置不同自主程度。

## 13. 对实施阶段的补充

在原阶段 A-H 中加入以下强制任务：

- 阶段 A：为现有上下文快照、citation、NDJSON 时间线建立黄金测试。
- 阶段 B：同时定义 `ContextAssembler`、`ContextBlock`、`ContextBudgetPolicy` 和 `PublicProgressEvent`。
- 阶段 C：实现 `LoopGuard`、结构化工作流状态和终止校验。
- 阶段 D：先迁 `retrieve_kb`，证明新 Action 不重写知识库，再迁 file/search/general。
- 阶段 E：实施前再次调用 `flyway_status`；当前核对结果为 latest V8，新迁移从 V9 起，但若仓库届时已有 V9 则顺延。
- 阶段 F：保持 NDJSON v1 向后兼容，先双写/对比新旧公开时间线，再切换主路径。
- 阶段 G：用 MAIN_CHAT、MARKET_ANALYSIS、ROLEPLAY、INTERVIEWER 验证不同上下文、知识库和工具策略。

新增验收：

- 相同输入与 Profile 能生成稳定的 Context Bundle fingerprint。
- 最终回答不会丢失本轮附件，远期摘要与近期原文不会重复注入。
- 知识库入库、向量结构和 retrieval-test 行为保持不变。
- citation 在实时流、历史回放和最终回答之间一致。
- 时间线能实时显示运行步骤，但不泄露 reasoning、系统提示和模型思考。
- Agent 能自主路由、选工具和结束，同时受 Profile、预算、Schema、guardrail 与 LoopGuard 约束。
