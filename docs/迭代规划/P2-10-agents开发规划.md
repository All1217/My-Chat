# My-Chat Agents 开发 — 入门与进阶行动方案

> **目标读者**：AI 大模型应用开发初学者  
> **前置知识**：已掌握本项目基础架构（Spring Boot + Spring AI ChatClient + `@Tool` + 可选 MCP）  
> **参考文档**：  
> - [Spring AI — Building Effective Agents](https://docs.spring.io/spring-ai/reference/api/effective-agents.html)  
> - [Spring AI — ChatClient](https://docs.spring.io/spring-ai/reference/api/chatclient.html)  
> - [Spring AI — Structured Output](https://docs.spring.io/spring-ai/reference/api/structured-output-converter.html)  
> - 本地官方示例：[06-agents/README.md](../../06-agents/README.md)  
> - 本项目：[AGENTS.md](../../AGENTS.md) | [P2-9-mcp协议规划.md](./P2-9-mcp协议规划.md) | [P0-7-工具调用和向量知识库竞争模型注意力解决方案.md](./P0-7-工具调用和向量知识库竞争模型注意力解决方案.md)

---

## 一、什么是 Agents？先建立直觉

### 1.1 一句话概括

**Agentic 系统** = 用多步 LLM 调用来完成复杂任务。关键不是「再堆一个框架」，而是按 Anthropic / Spring AI 的建议：**先用简单、可组合的 Workflow，再考虑完全自治的 Agent**。

### 1.2 Workflow vs Agent（官方核心区分）

| 类型 | 定义 | 谁决定下一步 | 典型场景 |
|------|------|-------------|---------|
| **Workflow** | LLM + 工具按**预定义代码路径**编排 | 你的 Java 代码 | 固定流水线、客服分流、批量翻译 |
| **Agent** | LLM **动态**决定流程与工具使用 | 模型自己 | 开放式任务、探索性排障 |

Spring AI 文档与 `06-agents` 示例当前重点是 **五种 Workflow 模式**（可预测、易测、适合企业场景）。完全自治 Agent 是更后的目标。

### 1.3 能力阶梯（对照你已学过的模块）

| 层级 | 行为 | 本项目现状 | 06-agents |
|------|------|-----------|-----------|
| 单次 LLM | 一问一答 | 基础能力 | — |
| **Enhanced LLM** | RAG / Tools / Memory | **已具备** | 作为编排单元 |
| **Workflow** | 多步 LLM，代码定路径 | **尚未接入** | **完整五模式 Demo** |
| Autonomous Agent | 模型自决下一步 | 未做 | 文档「Future Work」方向 |

**本项目当前定位**：Enhanced LLM（普通聊天 = 工具 + 记忆；知识库聊天 = RAG Advisor），内部有一次请求内的 **Tool-calling 循环**（模型决定调哪个 `@Tool` / MCP），但这**不是** `06-agents` 里的多步 Workflow 编排。

```
单次增强调用（My-Chat 今天）          Workflow（06-agents / 本规划目标）

用户 → ChatClient.stream()            用户 → Java 编排器
         ↕ 模型可多次调 Tool                  ├─ LLM 步骤 1
         → 最终文本流式返回                   ├─ LLM 步骤 2（吃上一步输出）
                                            └─ … → 汇总结果
```

---

## 二、本项目与 06-agents 对照

### 2.1 能力矩阵

| 能力 | My-Chat（`my-chat-server` / `my-chat-vue3`） | 06-agents |
|------|---------------------------------------------|-----------|
| ChatClient | `toolChatClient` / `ragChatClient` | 单一 ChatClient Bean |
| 会话记忆 | `MessageChatMemoryAdvisor` + JDBC | 演示用，非重点 |
| 本地工具 | `FileTools`（ls/cat/write/…） | 无 |
| 远程 MCP | 天气 demo + Smithery/Exa（需密钥） | 无 |
| RAG | `QuestionAnswerAdvisor` + pgvector | 无 |
| Chain / Parallel / Routing / Orchestrator / Evaluator | 无 | 有，含 Web UI |
| 中间步骤可视化 | 前端几乎看不到 tool/步骤 | Thymeleaf 分步展示 |

### 2.2 关键代码位置

| 角色 | 路径 |
|------|------|
| 双 ChatClient 装配 | `my-chat-server/.../config/AiConfiguration.java` |
| 普通流式聊天 | `my-chat-server/.../controller/ChatController.java` |
| RAG 流式聊天 | `my-chat-server/.../controller/RagChatController.java` |
| 本地文件工具 | `my-chat-server/.../tools/FileTools.java` |
| MCP 鉴权（Smithery） | `my-chat-server/.../config/SmitheryMcpConfiguration.java` |
| 前端流式 | `my-chat-vue3/src/utils/streamChat.ts`、`ChatBox.vue` |
| 五模式实现 | `06-agents/.../patterns/*.java` |
| 五模式服务入口 | `06-agents/.../service/AgentPatternsService.java` |

### 2.3 五种 Pattern 速查（来自 06-agents + 官方文档）

| # | Pattern | 何时用 | 本地类 |
|---|---------|--------|--------|
| 1 | **Chain** | 步骤固定、后一步依赖前一步，用延迟换准确 | `ChainWorkflow.java` |
| 2 | **Parallelization** | 独立子任务可并行（多语言、多视角） | `ParallelizationWorkflow.java` |
| 3 | **Routing** | 先分类再交给专用 prompt / 专用客户端 | `RoutingWorkflow.java` |
| 4 | **Orchestrator-Workers** | 子任务无法事先写死，由编排 LLM 动态拆分 | `OrchestratorWorkers.java` |
| 5 | **Evaluator-Optimizer** | 有明确质量标准，需「生成 → 评价 → 改写」循环 | `EvaluatorOptimizer.java` |

**实现共性**：不增加新的 Spring AI「Agent 框架」依赖；全部用 `ChatClient.prompt(...).call()`（配合 Structured Output）在 **Java 里编排**。

---

## 三、入门尝试（建议 1～2 周）

按顺序做。每完成一步勾选一次自查清单（第六节）。

### 阶段 A：跑通本地官方示例（约 0.5～1 天）

**目标**：亲眼看到五种模式的中间步骤，建立「编排在代码、模型只做单步」的直觉。

1. 打开 [`06-agents/README.md`](../../06-agents/README.md)，确认 Java 25 / Maven / 模型密钥。
2. 将 `06-agents` 的 `application.yaml` 配成与 My-Chat 一致的 DeepSeek（或其它 OpenAI 兼容）端点与 API Key（示例默认可能是 Azure，按你本机环境改）。
3. 启动模块（README 中 `start.ps1` / Dashboard），打开演示页（默认约 `http://localhost:8086`）。
4. 在 UI 中依次跑：
   - Chain（看 Extract → Standardize → Sort → Format）
   - Parallelization（感受总耗时接近最慢的一路）
   - Routing（确认分类标签 + 专用回复）
   - Orchestrator-Workers（看计划 JSON + 各 worker）
   - Evaluator-Optimizer（看多轮 refine）

**验收**：能向他人口述每个模式「谁决定下一步」。

### 阶段 B：精读源码（约 1～2 天）

**优先顺序**（由浅入深）：

1. `ChainWorkflow` — 最简单的 for 循环链式调用  
2. `RoutingWorkflow` — 分类 + Map 分发  
3. `ParallelizationWorkflow` — 线程池 / 并发  
4. `EvaluatorOptimizer` — 循环退出条件 + Structured Output  
5. `OrchestratorWorkers` — 动态子任务 + 汇总  

阅读时带着问题：

- 哪一段是「固定代码路径」？哪一段是「单次 LLM」？
- 若某一步返回空或坏 JSON，示例如何处理？你如何加强？
- Structured Output（`.entity(Xxx.class)`）用在哪些模式？

对照官方文档章节：[Building Effective Agents](https://docs.spring.io/spring-ai/reference/api/effective-agents.html)。

### 阶段 C：对照本项目「Enhanced LLM」（约 0.5 天）

在 My-Chat 正常对话中验证（需后端 8100；天气 MCP 可选启动 `mcp-server-demo`）：

| 实验 | 操作 | 你应理解到 |
|------|------|-----------|
| 工具循环 | 「列出工作区文件并读某个 txt」 | 模型在一次 stream 请求内多次调 `FileTools`，路径由模型决定 |
| MCP | 「查一下北京天气」或搜索类问题 | 远程工具与本地 `@Tool` 对模型透明 |
| RAG 隔离 | 知识库模式提问 | `ragChatClient` **无工具**，避免与检索抢注意力（见 P0-7） |

**关键认知**：Tool-calling ≠ Workflow。Tool-calling 是 Enhanced LLM 的能力；Workflow 是**多次独立 ChatClient 调用**的显式编排。

### 阶段 D：最小落地 — 把 Chain 搬进 My-Chat（约 2～3 天）

**原则**：先不进主聊天流，用独立调试 API，避免干扰现有 `/ai/normalChat` / `/ai/ragChat`。

建议改动（动手时再写代码，本规划只定方向）：

1. 新建包：`com.mychat.agent.patterns`  
   - 从 `06-agents` 移植 `ChainWorkflow`（可先拷贝再改包名与 ChatClient 注入方式）。
2. 新建 `AgentDemoController`（示例路径）：`POST /ai/agent/chain`  
   - 入参：文本  
   - 出参：建议返回**每一步的中间结果**（List 或 Map），便于学习与排错。
3. Bean：复用已有 `OpenAiChatModel` 构建一个**无 FileTools** 的简易 `ChatClient`，避免 Chain 步骤误调工具；或显式 `.defaultTools()` 不注册。
4. 验证：`curl` / IDEA HTTP Client 调用，对照 06-agents UI 行为。
5. **暂不改** `schema.sql`、前端路由；入门阶段以后端为准。

示例请求形态（示意）：

```http
POST /ai/agent/chain
Content-Type: application/json

{ "input": "本季度满意度 92 分，营收增长 45%，员工满意度 87 分……" }
```

期望：响应中能看到提取 → 标准化 → 排序 → 表格等分步输出。

### 阶段 E：第二模式 — Routing（约 1～2 天）

与现有双 ChatClient 天然契合：

| 路由标签 | 建议处理 |
|----------|----------|
| `file` | `toolChatClient`（文件 / MCP） |
| `kb` | `ragChatClient` + `kbId` |
| `search` | MCP 搜索类工具（Smithery/Exa）或专用 prompt |
| `general` | 纯对话 |

实现要点：

- 先用一次 LLM（或规则）产出路由标签（可用 Structured Output）。
- 再 `switch` 到对应客户端；**不要**把三种能力塞进一个超大 system prompt。
- 调试 API：`POST /ai/agent/route`，返回 `{ "route": "...", "answer": "..." }`。

**入门阶段验收标准**：

- [ ] 06-agents 五模式都能跑通并解释用途  
- [ ] 能说清 Workflow 与 Tool-calling 的区别  
- [ ] My-Chat 中有独立 Chain（及可选 Routing）调试接口，且不破坏现有聊天  

---

## 四、未来进阶规划

在入门验收通过后，按优先级推进（可与 P2-9 MCP 并行）。

### 进阶 1：Pattern 组合（2～3 周）

- **Routing → Chain**：先分流，再对「报告类」走 Chain 流水线。  
- **Orchestrator + Tools**：编排器拆任务，Worker 持有 `FileTools` / MCP（写文件、搜网、查天气）。  
- 注意：Worker 是否允许工具、允许哪些工具，用配置或 `McpToolFilter` 显式控制。

### 进阶 2：Evaluator-Optimizer + 工作区（2 周）

典型场景：「生成一段配置/文档 → 评价是否符合规范 → 用 `FileTools.write` 落盘 → 再 `cat` 校验」。

价值：把「模型写了就算完」变成「可度量的质量环」，贴近真实 Agent 产品。

### 进阶 3：可观测性（前端，2～3 周）

当前 `ChatBox` 几乎看不到 tool 名称 / 参数 / 结果，也不展示 Workflow 步骤。

建议：

- 后端在调试 API 或正式流中输出结构化事件（如 `step` / `tool_call` / `tool_result`）。  
- 前端时间线组件展示步骤（可参考 06-agents 各 pattern 页面的分步 UI）。  
- 与现有 thinking 标签解析并存，避免混在同一纯文本流里无法区分。

### 进阶 4：RAG + Tools 统一路径（架构级，3～4 周）

背景：P0-7 用双 ChatClient 避免「工具与检索抢注意力」。

进阶做法不是强行合并进一个 `defaultTools`+`QuestionAnswerAdvisor`，而是：

- 在 **Workflow** 内显式步骤：先检索 → 再决定是否调工具 → 再生成；或  
- Routing 将「纯问答」与「要改文件」彻底分开。

这样既保留可靠性，又支持「查知识库后把结论写入工作区」类任务。

### 进阶 5：记忆与长期状态（按需）

- 现有 `MessageWindowChatMemory`（64 条）适合对话，不适合跨天 Agent 任务状态。  
- 可探索：任务表（PostgreSQL）存 `plan` / `step` / `status`；或向量库存长期事实。  
- 涉及表结构时再改 `my-chat-server/src/main/resources/schema.sql`，入门阶段不要提前加表。

### 进阶 6：安全与边界（贯穿始终）

| 风险 | 措施 |
|------|------|
| 工具越权写盘 | 继续 `WorkspaceUtil.resolveSafe()` |
| MCP 工具过多/危险 | `McpToolFilter`、连接级开关 |
| 远程 MCP 不可用拖垮启动 | `initialized` / 软依赖 ObjectProvider（已有实践） |
| 无限 Evaluator 循环 | 最大迭代次数 + 超时 |
| Orchestrator 乱拆任务 | 限制 worker 数量与允许的工具集 |

### 进阶 7：对外暴露能力（可选，与 P2-9 衔接）

- 将 `FileTools` 以 `@McpTool` 形式做成 MCP Server，供外部 Agent / Cursor 调用。  
- 本项目的 Chat 则作为「带 Workflow 的 MCP Client + 本地工具」的宿主。

### 进阶路线图（示意）

```text
入门: 06-agents 跑通 → Chain/Routing 调试 API
  ↓
组合: Routing+Chain；Orchestrator+FileTools/MCP
  ↓
质量: Evaluator-Optimizer + 写盘校验
  ↓
产品化: 步骤可视化；RAG↔Tools 显式编排
  ↓
深化: 任务状态持久化；MCP Server 化；安全加固
```

---

## 五、学习资源索引

| 主题 | 资源 |
|------|------|
| 五种模式原理与图示 | [Effective Agents](https://docs.spring.io/spring-ai/reference/api/effective-agents.html) |
| 本地可运行 Demo | [06-agents/README.md](../../06-agents/README.md) |
| ChatClient API | [ChatClient](https://docs.spring.io/spring-ai/reference/api/chatclient.html) |
| 结构化输出（Routing / Orchestrator / Evaluator 常用） | [Structured Output](https://docs.spring.io/spring-ai/reference/api/structured-output-converter.html) |
| 工具调用 | [Tool Calling](https://docs.spring.io/spring-ai/reference/api/tools.html) |
| MCP 接入 | [P2-9-mcp协议规划.md](./P2-9-mcp协议规划.md) |
| 为何 RAG 与 Tools 拆开 | [P0-7-工具调用和向量知识库…](./P0-7-工具调用和向量知识库竞争模型注意力解决方案.md) |
| 项目架构速查 | [AGENTS.md](../../AGENTS.md) |

### 学习路径对照表

| 步骤 | 官方文档 | 本地对应 |
|------|----------|----------|
| 理解 Workflow vs Agent | Effective Agents 开篇 | 06-agents README「What Are Agents?」 |
| Chain | Chain Workflow 节 | `ChainWorkflow.java` |
| Parallel | Parallelization 节 | `ParallelizationWorkflow.java` |
| Routing | Routing 节 | `RoutingWorkflow.java` |
| Orchestrator | Orchestrator-Workers 节 | `OrchestratorWorkers.java` |
| Evaluator | Evaluator-Optimizer 节 | `EvaluatorOptimizer.java` |
| 接到本项目 | — | 本规划第三节阶段 D/E |

---

## 六、问题自查清单（边做边对照）

### 概念

- [ ] 我能用自己的话区分 Workflow 和 Autonomous Agent 吗？  
- [ ] 我能说明 My-Chat 的 Tool-calling 属于哪一层，而不是「已经做了 Agent Workflow」吗？  
- [ ] 我知道为何企业场景往往优先 Workflow 而不是完全自治吗？  

### 06-agents

- [ ] 五种模式我都能在 UI 里跑通，并指出各自适用场景吗？  
- [ ] 我能在 `ChainWorkflow` 里指出「代码编排」与「单次 LLM」的边界吗？  
- [ ] 我理解 Parallel 用延迟换吞吐、Chain 用延迟换准确吗？  

### My-Chat 落地

- [ ] 我有独立的 `/ai/agent/chain`（或等价）调试接口，且主聊天未回归吗？  
- [ ] Routing 能否把「文件 / 知识库 / 闲聊」分到不同处理路径？  
- [ ] 出问题时，我能区分「某一步 LLM 失败」和「工具/MCP 失败」吗？  

### 进阶准备

- [ ] 前端是否已有（或已规划）步骤 / tool 可视化？  
- [ ] 是否明确 Evaluator 的最大轮数与退出条件？  
- [ ] 是否阅读过 P2-9，知道 MCP 与 Agent 编排如何叠加？  

---

## 七、相关文件索引（规划落地时）

| 文件 / 目录 | 说明 |
|-------------|------|
| `06-agents/src/main/java/.../patterns/` | 学习与移植源 |
| `my-chat-server/.../config/AiConfiguration.java` | 现有 Enhanced LLM 装配；Agent 调试客户端可另建 Bean |
| `my-chat-server/.../agent/patterns/`（建议新建） | 移植后的 Workflow 类 |
| `my-chat-server/.../controller/AgentDemoController.java`（建议新建） | 入门调试 API |
| `my-chat-vue3/src/components/ChatBox.vue` | 进阶：步骤可视化 |
| `my-chat-server/src/main/resources/schema.sql` | 进阶任务状态表时再改；入门勿动 |
| `docs/迭代规划/P2-9-mcp协议规划.md` | 工具侧扩展 |
| `docs/迭代规划/P0-7-*.md` | RAG/Tools 拆分背景 |

---

## 八、推荐第一周日程（可压缩）

| 天 | 任务 |
|----|------|
| D1 | 跑通 06-agents；记五种模式笔记 |
| D2 | 精读 Chain + Routing 源码 |
| D3 | My-Chat 对照实验（Tools / RAG / MCP） |
| D4～D5 | 移植 Chain + `/ai/agent/chain` |
| D6～D7 | 实现 Routing 调试 API；写一篇个人笔记（可选提交到 `docs/`） |

完成第一周后，再从第四节「进阶 1」选一条主线深入，避免同时开太多架构改造。

---

*文档版本：P2-10｜与 Spring AI 2.0.0、本仓库 `06-agents` 示例对齐。随官方文档与示例更新可修订「模式类名 / 端口」等细节。*
