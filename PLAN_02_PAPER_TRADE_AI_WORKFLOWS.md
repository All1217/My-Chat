# 行动计划二：模拟炒股 AI 工作流

> 前置依赖：`PLAN_01_GENERAL_AGENT_ORCHESTRATION.md` 已完成并通过验收。本计划不再实现独立编排循环，只复用通用 `AgentRuntime`、Profile、Action、Tool Policy、Guardrail 和 Trace。

## 1. 目标

把“搜集员”和“交易员”正式定义为两条领域 AI 工作流，明确一次定时任务周期内：

- 经历哪些确定性业务环节。
- 每个环节调用哪些 Java 能力或 Agent 工具。
- 哪些环节发起 AI 编排。
- AI 可以自主决定什么、禁止决定什么。
- 每一步的输入、输出、失败、重试、幂等和终态。

工作流既用于学习大模型应用开发，也必须保持模拟交易的资金安全与可解释性。

## 2. 总体设计

两条工作流采用“领域 Workflow + 通用 Agent Runtime”的混合模式：

- 外层领域 Workflow 固定关键顺序，保证状态、风控和副作用可控。
- 内层 AI 节点复用通用编排，可在 Profile 允许的只读动作中规划、调用工具、反思和生成结构化结果。
- AI 不直接写数据库、不直接修改账户、不直接撮合订单。
- 搜集员可以在证据收集阶段使用受限检索工具。
- 交易员只能读取已批准的账户、持仓、研究和行情上下文；订单必须经过确定性的校验、风控和账本服务。
- 所有领域步骤与 Agent 子步骤进入同一层级 Trace。

复用关系：

- `async_job`：定时任务异步调度和通知。
- `paper_research_run` / `paper_ai_run`：领域轮次与业务结果。
- `agent_run` / `agent_run_step`：通用 Agent 执行、工具、模型、guardrail 和 checkpoint。
- `business_ref`：关联领域 run ID。

## 3. 搜集员工作流

场景 Profile：`PAPER_RESEARCH`

### 3.1 Profile 权限

允许的只读能力：

- `market.list_candidates`：市场候选列表。
- `market.get_quote`：日 K、价格、成交额和换手率。
- `market.get_symbol_facts`：ST、停牌、涨跌停、上市时间、流动性。
- `research.get_announcements`：公告。
- `research.get_news`：个股新闻。
- `research.get_sector_briefs`：与标的名称相关的板块材料。
- `research.web_search`：仅在设置打开时启用，且限制结果数、域名、超时和调用次数。
- `strategy.read_default`：读取当前投资策略。

禁止：

- 账户修改、研究卡直接写入、订单、删除和任意 SQL。
- 扩展到候选池外的新代码。
- 使用 file、通用 MCP 或与本场景无关的知识库工具。

### 3.2 周期步骤

1. `START_RUN`
   - cron 或手动触发。
   - 创建 `paper_research_run`、`async_job` 和关联 `agent_run`。
   - 检查是否已有活动搜集轮次，防止重叠执行。

2. `DISCOVER_CANDIDATES`
   - 确定性调用 `PaperStoryFeed.page`。
   - 应用最近研究、ST、最低一手资金和涨停提示等初筛。
   - 输出候选代码及初筛统计。

3. `VALIDATE_MARKET_DATA`
   - 调用 `MarketQuoteClient`、`PaperSymbolGate`、`MarketDipIndicators`。
   - 检查行情可用性、上市根数、流动性、相对低点。
   - 单票失败可跳过；全批行情失败则整轮失败。

4. `AI_COLLECT_AND_ANALYZE`
   - 向 `AgentRuntime(PAPER_RESEARCH)` 提交已验证候选、策略和工具预算。
   - Agent 可按需调用公告、新闻、板块材料和受限 Web 搜索。
   - 允许使用通用 orchestrator-workers：按标的拆分只读研究 Worker，最后汇总。
   - 允许 evaluator-optimizer 做一次证据充分度复核；只有明确评分规则时启用。
   - 输出类型化 `ResearchCardBatch`。

5. `VALIDATE_CARDS`
   - Schema 校验并自动修复非法 JSON。
   - 领域 guardrail 校验 symbol 属于候选池、评分 0-1、来源可追溯、`kept=true` 必须满足最低证据要求。
   - 批次上限由 Java 强制执行，模型不能覆盖。

6. `UPSERT_NOTES`
   - 由 `PaperResearchService` 写 `paper_research_note`。
   - Agent 没有该写权限。
   - 保存 source 引用、评分、标签、story、risks 和 run ID。

7. `FINISH_RUN`
   - 更新 screened/kept、终态、错误和完成时间。
   - 领域 trace 引用 Agent trace，不复制敏感工具原文。
   - batch 为空可成功结束；模型或工具失败按策略决定失败，不把无证据结果当成功卡片。

### 3.3 输出契约

定义 `ResearchCardBatch` 和 `ResearchCardDecision`：

- `symbol`、`name`、`kept`。
- `story`、`risks`、`rejectReason`。
- `fitScore`、`evidenceScore`、`signalTags`。
- `evidenceRefs`：引用工具返回材料 ID，不允许凭空生成 URL。

使用 Spring AI 2.0 结构化输出和 Schema 验证，不再手工截取 `[...]` 作为主路径。

## 4. 交易员工作流

场景 Profile：`PAPER_TRADER`

### 4.1 Profile 权限

允许的只读能力：

- `paper.read_account`：现金、权益、回撤和买入暂停。
- `paper.read_positions`：持仓、可用数量、成本和出场参数。
- `paper.read_cooldowns`：冷却代码。
- `paper.read_research_notes`：`kept=true` 与允许的 watchlist。
- `market.get_quote`、`market.get_symbol_facts`、`market.get_etf_premium`。
- `paper.read_recent_decisions`、`paper.read_latest_stage_summary`。
- `strategy.read_default`。

禁止：

- `place_order`、修改账户、直接写 decision、删除历史。
- 搜索候选池外标的。
- 通用 Web 搜索、文件和无关 MCP。

交易员的 AI 输出只是“建议决策”；执行权属于领域 Workflow。

### 4.2 周期步骤

1. `START_RUN`
   - cron 或手动触发。
   - 创建 `paper_ai_run`、`async_job` 和关联 `agent_run`。
   - 账户级互斥，禁止多个交易员轮次并发。

2. `PROCESS_RULE_EXITS`
   - `PaperPositionExitService` 先执行止损、止盈、最长持有期规则。
   - 这是确定性规则步骤，不调用 AI。
   - 通过 `PaperRiskGuard` 和 `PaperTradeService` 执行。

3. `BUILD_CONTEXT`
   - `PaperAiContextBuilder` 汇总账户、持仓、冷却、研究卡、行情、ETF 溢价、最近决策与阶段总结。
   - 上下文过大时由通用 Context Policy 压缩，但关键资金和风险字段不得摘要丢失。

4. `AI_REVIEW_AND_DECIDE`
   - 调用 `AgentRuntime(PAPER_TRADER)`。
   - Agent 可按需读取候选的行情与研究材料，进行多步分析和一次自我评估。
   - 不允许 Web 搜索新标的，不允许调用写工具。
   - 输出类型化 `TradeDecisionBatch`，动作限于 BUY/SELL/HOLD/FLAT。

5. `VALIDATE_DECISIONS`
   - Schema 校验和字段别名兼容。
   - 校验 symbol 在候选池或持仓池、价格与数量存在、A 股整手、置信度范围、止盈止损关系。
   - 非法项单独拒绝，并保留 guardrail 原因。

6. `APPLY_RISK_GATES`
   - 调用 `PaperRiskGuard`。
   - 检查买入暂停、最大持仓、冷却、涨跌停、流动性、ETF 溢价、仓位、风险预算、T+1 和佣金比例。
   - Java 可缩减数量或拒绝，AI 不得覆盖。

7. `EXECUTE_ORDERS`
   - 只有通过 guardrail 的决策进入 `PaperTradeService.placeOrder`。
   - 每个决策使用稳定幂等键；检查已有 `order_id`，禁止重放成交。
   - 逐条回写 EXECUTED/SKIPPED/REJECTED/PENDING。

8. `SETTLE_AND_FINISH`
   - 更新权益、峰值、回撤和本轮统计。
   - 若部分订单已执行后发生异常，标记为带部分执行摘要的失败，不自动整轮重试。
   - 结束 `paper_ai_run`、`agent_run` 和 `async_job`。

### 4.3 输出契约

定义 `TradeDecisionBatch` 和 `TradeDecisionProposal`：

- `action`、`symbol`、`name`。
- `quantity` 或 `capitalRatio`。
- `referencePrice`、`takeProfit`、`stopLoss`、`maxHoldDays`、`cooldownDays`。
- `confidence`、`reasoning`、`riskFlags`、`manualReview`。
- `evidenceRefs`：引用上下文或工具材料。

字段 Schema 是唯一正式契约；`shares/price/reason` 等仅作为兼容别名保留。

## 5. 代码实施

### 5.1 注册场景 Profile

新增：

- `PaperResearchAgentProfile`
- `PaperTraderAgentProfile`
- `PaperTradeActionConfiguration`
- `PaperTradeGuardrails`

Profile 只注册场景能力，不实现新的 Agent 循环。

### 5.2 适配现有领域能力

将以下组件包装为通用 `AgentActionHandler` 或 ToolCallback：

- `PaperStoryFeed`
- `MarketQuoteClient`
- `PaperSymbolGate`
- `PaperMacroFeed`
- `PaperResearchWebEnricher` 的底层搜索能力
- `PaperAiContextBuilder` 中的只读查询能力
- `PaperStrategyService`

高风险写能力不注册为模型工具，继续由工作流 Java 代码直接调用。

### 5.3 重构领域服务

在 `PaperResearchService.run` 与 `PaperAiTraderService.runRound` 中：

- 明确拆分上述固定步骤，每个方法保持单一职责。
- 使用 `WorkflowRunner` 执行领域步骤。
- AI 步骤通过 `AgentRuntime`，传 `business_ref=paper run id`。
- 删除 `PaperFrameworkCall` 对旧 `AgentOrchestratorService` 的直接依赖；迁移完成后删除该适配层。
- 统一异常分类：外部依赖、模型输出、guardrail、领域冲突、持久化和未知异常。

### 5.4 生命周期与幂等

- run 使用 `PENDING -> RUNNING` 条件更新，重复 handler 不执行。
- 提交前检查同类活动 run；交易员额外加账户级互斥。
- Agent 只读步骤可有限重试。
- 搜集卡 upsert 可按 symbol/run 幂等。
- 订单执行按 decision/order 幂等，任何已执行订单都阻止整轮自动重放。
- 通用 stale recovery 同步关闭关联 `paper_*_run`。

## 6. 数据库与 Flyway

本计划优先复用计划一新增的 `agent_run`、`agent_run_step`、`agent_approval`。

先核对实施时最新 Flyway：

- 若现有 `paper_*_run` 能通过 `agent_run.business_ref` 反查，则不新增字段。
- 若关联查询性能或唯一性不足，再在下一顺序 Flyway 为 `paper_research_run`、`paper_ai_run` 增加 nullable `agent_run_id` 和索引。
- 不为每个领域步骤新建模拟交易专属 step 表。

所有迁移必须先用 `mcp-database-server.flyway_status` 与 `describe_tables` 核对本地结构；MCP 不可用时按项目约束报告并回退到 migration 文件。

## 7. 历史页与可观测性

扩展模拟交易历史页：

- 增加“交易轮次”资源。
- 搜集轮次和交易轮次展示紧凑步骤时间线。
- 领域步骤显示状态、耗时、摘要和错误。
- AI 子步骤显示模型调用、工具调用、guardrail、handoff 与 evaluator 结果。
- 默认隐藏长 prompt、原始上下文和工具全文，详情按需展开。
- `paper_ai_run` 加入 SQL 导出白名单。
- 从 async job 通知可跳转到对应 run 详情。

## 8. 测试计划

### 搜集员

- 空候选成功。
- 单票行情失败继续、全批行情失败终止。
- Agent 按需调用公告/新闻/Web 工具。
- 候选池外 symbol 被拒绝。
- 缺评分、证据引用无效、Schema 非法的自动修复与最终失败。
- 重复 handler 不重复写卡。

### 交易员

- 规则出场不依赖 AI。
- Agent 只能看到只读工具。
- 非候选 BUY、缺价格、非整手、风险闸门拒绝。
- 通过闸门后只成交一次。
- 部分成交后异常不自动重放。
- 并发手动/cron 只允许一个账户轮次。

### 通用能力复用

- 两个 Profile 均由同一 `AgentRuntime` 执行。
- Profile 工具隔离测试证明交易员看不到 Web 搜索和订单工具。
- Run/Step Trace 与领域 run 正确关联。
- checkpoint 恢复不会重复副作用。

## 9. 验收标准

- 搜集员和交易员的完整步骤、工具和 AI 行为可从历史详情解释。
- 两条工作流没有自己的 Agent 循环或通用 Worker switch。
- AI 可以进行多步研究和评估，但不能绕过候选池、风控和账本。
- 所有结构化输出通过 Schema 校验；解析不再主要依赖文本截取。
- 定时与手动触发共用同一工作流，且具备互斥和幂等。
- 失败能定位到具体领域步骤或 Agent Span。
- 交易写操作在重试、恢复和重复投递下均不会重复成交。
- 新增能力不破坏现有账户、研究卡、决策、订单和历史导出行为。
