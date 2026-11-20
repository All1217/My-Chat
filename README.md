# My-Chat · Agent + RAG 学习实验平台

[![Java](https://img.shields.io/badge/Java-25-orange?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-green?logo=springboot)](https://spring.io/projects/spring-boot)
[![Vue 3](https://img.shields.io/badge/Vue%203-3.5-brightgreen?logo=vue.js)](https://vuejs.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-42.7-blue?logo=postgresql)](https://www.postgresql.org/)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-2.0-purple?logo=spring)](https://spring.io/projects/spring-ai)

---

## 项目介绍

My-Chat 是个人学习与交流用的大模型应用开发实验平台：在 **Vue 3 + Spring Boot + Spring AI** 上先搭一套可编程的**自循环 Agent**（Orchestrator-Workers + 通用 Agent Runtime），再以此为底座重点研究 **RAG 的实现与优化**，并扩展模拟炒股等自用功能包。整体供自己动手实验，不是面向生产的通用产品。

- **Agent 底座**：用户一句 → 领班决定下一步 → 工人执行 → 观察再决策，直到收工；主聊天是一条显式回合流水线（`ChatTurnStage`），写盘后可跑质量环。知识库、工作区、联网搜索与闲聊挂在同一条主聊天链路上。
- **通用 Agent Runtime**：场景通过 `AgentScenarioProfile` 注册（动作 / 工具白名单 / 步数预算 / 输出策略 / 质量策略），运行态写入 `agent_run` / `agent_run_step` / `agent_approval`，支持 checkpoint、审批记录与 Actuator 指标。
- **RAG 研究重点**：知识库隔离检索（目录总览 / 向量召回）、引用回显、异步入库、召回测试与只读分段预览；主聊天由 `retrieve_kb` Worker 调用。当前与后续主攻召回质量、分段与引用、入库与评测等。
- **自用扩展**：工作区 FileTools / ShellTool、MCP（数据库 / 爬虫 / Smithery）、模拟炒股（账本 + 研究卡片 + AI 操盘 + 五条定时工作流）等，按需使用，不作项目主目标。

项目地址：[https://github.com/All1217/My-Chat](https://github.com/All1217/My-Chat)  
克隆：`git clone https://github.com/All1217/My-Chat.git`

架构与设计长文见 [docs/my-chat项目介绍.md](docs/my-chat项目介绍.md)；迭代条目见 [docs/迭代规划.md](docs/迭代规划.md)。

---

## 主聊天怎么跑

```text
网页发送
  → ChatController（校验 ndjson、绑工作区 / kbId、处理附件）
  → ChatTurnPipeline 六站
        emit_route → load_dialogue → assemble_context → orchestrate → stream_final → quality_loop
  → ChatTurnFinalizer（成功写 Memory + done；轨迹写入 chat_assistant_turns）
```

第 4 站 `orchestrate` 内是通用 Agent Runtime 自循环：`decideNext` → `retrieve_kb` / `file` / `search` / `shell` / `general` → 观察，最多 6 步、8 次工具调用后 `finish`。传输是 **NDJSON**（`application/x-ndjson`），不是经典 SSE；前端边收边画 route / step / text_delta / 引用。

---

## 技术栈

### 后端

| 技术 | 版本 | 说明 |
|------|------|------|
| Java | 25 | 开发语言（虚拟线程已开） |
| Spring Boot | 4.1.0 | 应用框架 |
| Spring AI | 2.0.0 | ChatClient / 工具 / pgvector / MCP |
| MyBatis-Plus | 3.5.15 | ORM |
| PostgreSQL | 42.7.3 | 业务库 + pgvector |
| Flyway | Boot 托管 | 结构迁移（V1–V14） |
| Caffeine | Boot 托管 | 进程内缓存（不上 Redis） |
| Spring AOP / Mail / Actuator | Boot 托管 | 工具审计、评估邮件、运行指标 |
| PDFBox / Apache POI / jsoup | 2.0.31 / 5.4.0 / 1.18.3 | PDF / Office / HTML 解析 |
| OkHttp | 4.12.0 | 行情与搜索 HTTP 客户端 |

默认对话模型走 DeepSeek（OpenAI 兼容，`deepseek-flash`），向量走阿里云 MaaS `text-embedding-v4`（1536 维）。设置里可配置并切换对话模型；供应商预设含 DeepSeek / OpenAI / 通义千问 / Ollama / 硅基流动 / 自定义。

### 前端

| 技术 | 版本 | 说明 |
|------|------|------|
| Vue 3 | 3.5.x | UI |
| TypeScript | 5.6.x | 类型 |
| Vite | 6.x | 构建；`/rag` 代理到 `:8100` |
| Element Plus | 2.9.x | 组件库 |
| Pinia | 2.3.x | 状态 |
| ECharts | 6.x | 股市图表 |
| Vue Flow | 1.48.x | 模拟炒股可视化工作流 |
| marked / DOMPurify / highlight.js | — | Markdown 渲染、消毒、代码高亮 |
| mammoth / xlsx | — | 前端 docx / xlsx 预览解析 |

---

## 功能清单

### Agent 主聊天

- 多步编排：知识库 / 文件 / 搜索 / 命令行 / 闲聊 Worker 接力，时间线可回放
- 流式 NDJSON；步骤、工具调用、KB 引用标签（文档目录 / 向量检索）
- 会话绑定 `kbId`、`workDir`；滚动摘要 + 近期原文窗口
- 聊天附件 txt / md / pdf（正文只进本轮，Memory 只留文件名 + 原问；单文件 50,000 字符截断）；图片前后端双拒
- 写盘后可选 Evaluator-Optimizer 质量环（`qualityLoop`，默认开）
- 回合轨迹按轮写入 `chat_assistant_turns`（`parts` JSONB），刷新后可回放

### Agent Runtime（通用底座）

- 场景 Profile：`MAIN_CHAT` / `MARKET_ANALYSIS` / `ROLEPLAY` / `INTERVIEWER` / `PAPER_RESEARCH` / `PAPER_TRADER` / `PAPER_WAREHOUSE_ANALYST` / `PAPER_TRADE_EVALUATOR`
- 工具安全等级：`READ_ONLY` / `REVERSIBLE_WRITE` / `HIGH_RISK_WRITE`；工具白名单与动作子集取交集
- `agent_run` / `agent_run_step` / `agent_approval`：状态机、checkpoint、幂等键、审批记录、预算耗尽
- `StructuredOutputRepairService` 修复类型化输出；公开进度经 `PublicProgressAdapter` 脱敏
- Micrometer 指标：运行数、耗时、token、工具错误、预算耗尽

### RAG 知识库（重点研究方向）

- 知识库 CRUD；按库隔离的向量检索；`catalog` / `vector` 两种范围
- 批量上传（PDF / DOCX / XLSX / HTML / TXT / MD），异步 Job 切片 + 向量化
- 库级切分 / topK / 相似度阈值；文档重新向量化；只读分段预览
- 召回测试（只检索、不生成）；聊天里带来源 filename / score / 摘录
- 入库进度走通知中心（SSE `/ai/jobs/events`）

尚未做、计划贴近知识库的：父子分段、Rerank、混合检索、库统计仪表盘、引用与评测体验。见 [docs/迭代规划.md](docs/迭代规划.md)。

### 工作区

- 会话级工作目录；目录树（含懒加载）/ 预览 / 增删改 / 导入
- `FileTools` 10 个工具：ls、tree、cat、grep、stat、write、mkdir、rm、mv、cp
- `ShellTool`：构建 / git / 脚本；60 秒超时、输出截断、关机 / 磁盘类危险命令拦截
- 目录选择器 + 安全校验（拒绝系统关键路径）

### 模拟炒股（自用功能包，体量最大的应用）

> 虚拟盘，不接实盘券商；行情取东财收盘价做市价撮合。

- 账本：A 股整手、T+1、佣金 / 印花税、冷却、止盈止损 / 到期自动出场
- 研究卡片：价值等级 0–3、自选、契合 / 证据分、信源层级（A_ONLY / A_PLUS_WEB）、单票补资料
- AI 操盘：策略半可编辑（锁定硬约束 + 可编辑风格 + 结构化风控）、结构化决策、风控闸门、人工确认
- 五条定时工作流（账户设置里改 cron / 开关，保存后动态重挂）：
  - 搜集员（默认每天 09:00，默认关）
  - 交易员盘后（默认工作日 15:35）
  - 票仓分析员（默认每天 18:00，Agent 深搜后调 `updateValueTier`）
  - 票仓清理（默认每天 23:00，无 AI，按滞留天数降级 / 删除）
  - 交易评估员（默认每周六 10:00，写阶段总结 + 发邮件；资金枯竭时唤醒）
- 可视化工作流：只读蓝图 + 最近一轮 `framework_trace` 高亮
- 历史表 CRUD / 全表 INSERT.sql 导出；Agent 工具写库审计（`paper_ai_tool_audit`）
- 本地爬虫连通性探测；评估邮件未配 SMTP 时返回 `SKIPPED`，正文落日志

### 股市分析

- 行情 / K 线、Agent 未来情景推演（异步）
- 投资策略保存与 AI 评价 / 一键优化
- 自选股抄底提醒、股灾风险预警、美股指数估值

### 角色扮演 / 设置

- 角色扮演：独立页，走 `ROLEPLAY` Profile（无工具、最多 4 步）
- 设置：大模型管理（供应商 / 测试连通 / 设默认）、工作区管理、Prompt 管理、角色卡片、MCP 工具管理

### MCP / 联网搜索

- 本地数据库 MCP（`mcp-server-database`，:8102）、本地网页抓取 MCP（`mcp-server-crawler`，:8103，Python FastMCP）、Smithery 工具箱
- 惰性握手；单连接失败不拖垮启动；设置页运行时开关立即生效（`mcp_connection_settings`）
- 联网搜索优先本机 `searchWeb`（Exa → Bocha → Tavily → DuckDuckGo）

### 运维

- Flyway 管理结构（V1–V14），全新空库启动自动迁移
- `deploy/`：Compose 跑 app + nginx + crawler；Jenkins 发布；Dozzle / Adminer 走 SSH 隧道
- 云上只常驻 crawler MCP，database MCP / Smithery 默认关闭

---

## 项目结构

```
my-chat-server/src/main/java/com/mychat/
  controller/              # 主聊天、历史、知识库、工作区、任务、模型、MCP 设置
  agent/                   # 通用 Runtime、Profile、Action、Tool 白名单、Trace、持久化
  service/agent/           # 主聊天 Runtime、Context、质量环
  service/agent/pipeline/  # 回合流水线 ChatTurnStage
  service/knowledge/       # RAG 入库 / 检索 / 分段
  service/chat/            # 会话、Memory、时间线回合
  apps/
    market/                # 股市分析
    papertrade/            # 模拟炒股（ledger / trader / research / risk / warehouse / evaluator / schedule / workflow / audit / mail）
    roleplay/              # 角色扮演（后端仅占位）
  mcp/                     # MCP 桥、Tolerant Provider、运行时开关
  job/                     # 异步任务 + SSE 通知
  tools/                   # FileTools、ShellTool、WebSearchTools
  config/                  # ChatClient、Agent、MCP、缓存、工作区上下文

my-chat-vue3/src/
  views/                   # 聊天、知识库、设置、大厅骨架
  apps/                    # 可挂应用（registry + paper-trade + market + roleplay）
  components/              # ChatBox、时间线、KB 引用、目录选择器、Markdown 渲染
  composables/             # useChatStream、useFileUpload、useChatScroll
  utils/http/              # ragClient 拦截器（脱壳 + 校验 code）
  api/                     # chat / knowledge / workspace / jobs / model
  utils/streamChat.ts      # NDJSON 流式客户端（fetch）

mcp-server-database/       # 本地数据库 MCP（:8102）
mcp-server-crawler/        # 本地网页抓取 MCP（:8103，Python）
deploy/                    # Compose + Dockerfile + Jenkins + nginx
docs/                      # 项目介绍、迭代规划、部署、Agent Runtime、MCP、Flyway、大厅接入
```

---

## 快速开始

### 环境要求

| 依赖 | 版本 | 必需 | 说明 |
|------|------|------|------|
| JDK | 25 | 是 | 后端 |
| Node.js | 18+ | 是 | 前端 |
| Maven | 3.6+ | 是 | 或使用仓库内 `mvnw` |
| PostgreSQL | 14+ | 是 | 需 pgvector |

### 1. 环境变量

| 变量 | 用途 |
|------|------|
| `PGSQL_PASS` | PostgreSQL 密码 |
| `OPENAI_API_KEY` | 对话模型（默认 DeepSeek） |
| `EMBEDDING_MODEL_API_KEY` | 向量模型 |
| `EXA_API_KEY` | 可选；本机网页搜索（不要填 Smithery 的 key） |
| `SMITHERY_API_KEY` | 可选；MCP 工具箱 |
| `BOCHA_API_KEY` / `TAVILY_API_KEY` | 可选；搜索兜底 |
| `MCP_CRAWLER_URL` | 可选；默认 `http://localhost:8103`，Compose 内为 `http://crawler:8103` |
| `APP_MCP_ENABLED` / `APP_MCP_DATABASE_ENABLED` / `APP_MCP_CRAWLER_ENABLED` / `APP_MCP_SMITHERY_ENABLED` | 可选；MCP 总开关与分连接开关 |
| `PAPER_MAIL_HOST` / `PAPER_MAIL_PORT` / `PAPER_MAIL_USER` / `PAPER_MAIL_PASS` | 可选；交易评估员发信（默认 QQ SMTP，未配齐则跳过发信） |

### 2. 初始化数据库

数据库结构由 Flyway 自动管理，迁移文件位于 `my-chat-server/src/main/resources/db/migration/`：

| 版本 | 内容 |
|------|------|
| V1 | 基础通用（会话、Memory、回合、知识库、文档、异步任务等） |
| V2 | 股市预测 / 分析 |
| V3 | 模拟炒股账本、策略、AI 轮次 |
| V4 | 模拟炒股风控闸门 + 阶段总结 |
| V5 | 研究搜集 + AI 轮次轨迹列 |
| V6 | 模拟炒股定时配置 |
| V7 | 研究卡片自选 + 是否纳入自选 |
| V8 | 搜集信源层级 + 契合 / 证据分 |
| V9 | Agent Runtime 持久化（run / step / approval） |
| V10 | MCP / 搜索连接运行时开关 |
| V11 | 研究卡片价值等级 |
| V12 | 票仓分析员 / 清理 + 工具审计表 |
| V13 | 策略半可编辑 + 结构化风控表 |
| V14 | 交易评估员定时 + 收件人 |

全新空库在后端首次启动时自动执行 V1–V14。前置条件是 PostgreSQL 已安装 **pgvector**，且连接用户有权执行 `CREATE EXTENSION`，或管理员已提前创建 `vector`、`uuid-ossp` 扩展。

已有手工初始化的非空库首次接管前先备份，然后仅在第一次启动时设置：

```powershell
$env:FLYWAY_BASELINE_ON_MIGRATE="true"
```

Flyway 会把现有结构标记为 version 3，不重复执行 V1–V3。确认 `flyway_schema_history` 已生成后，删除该环境变量并保持默认 `false`，避免误连数据库时被静默接管：

```powershell
Remove-Item Env:FLYWAY_BASELINE_ON_MIGRATE
```

后续结构变更只新增更高版本的 `V{版本}__{说明}.sql`，已执行的迁移文件禁止修改。对话模型、默认模拟账户与默认策略仍由应用首次启动写入。

多机 / 换机如何对齐库结构（空库迁移、dump/restore、何时才能 baseline）见 [docs/Flyway多机数据库对齐指引.md](docs/Flyway多机数据库对齐指引.md)。

阿里云 CentOS 7.9：部署、Jenkins、日志（Dozzle）、查库（Adminer）见 [docs/部署与运维指引.md](docs/部署与运维指引.md)（样例 [`deploy/`](deploy/)）。

### 3. 启动后端

```bash
cd my-chat-server
.\mvnw.cmd spring-boot:run
```

`http://localhost:8100`。启动时会准备 `app.workspace.root`。MCP 为惰性握手，远端 404 不会阻止启动。

可选：启动本地数据库 MCP（Cursor / 主聊天共用，端口 8102）：

```bash
cd mcp-server-database
.\mvnw.cmd spring-boot:run
```

可选：启动本地网页抓取 MCP（通用 HTML 正文，端口 8103）：

```bash
cd mcp-server-crawler
.\scripts\run.ps1
```

详见 [`mcp-server-database/README.md`](mcp-server-database/README.md)、[`mcp-server-crawler/README.md`](mcp-server-crawler/README.md)。`.cursor/mcp.json` 不入库，换机后请按 [docs/Cursor本地MCP接入提醒.md](docs/Cursor本地MCP接入提醒.md) 自行配置。

### 4. 启动前端

```bash
cd my-chat-vue3
npm install
npm run dev
```

`http://localhost:5173`，API 经 `/rag` 转到 8100。

### 5. Flyway 分步验证

测试迁移 `src/test/resources/db/flyway-test/V4__create_flyway_smoke_test.sql` 是早期冒烟脚本，只在显式加入测试 classpath 和 migration location 时执行，不会进入生产包。**注意**：业务迁移现已到 V14，测试脚本仍编号 V4，同时挂载两个 location 会因版本重复而失败；重跑前先把它重命名为 `V15__create_flyway_smoke_test.sql`（并同步下方断言）。请使用独立测试库，不要直接测试当前业务库。

#### 5.1 验证全新空库

1. 创建空数据库 `my_chat_flyway_empty`，确认该 PostgreSQL 实例已安装 pgvector。
2. 在 PowerShell 设置测试连接及测试迁移目录：

```powershell
$env:SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/my_chat_flyway_empty?currentSchema=public"
$env:SPRING_FLYWAY_LOCATIONS="classpath:db/migration,classpath:db/flyway-test"
$env:FLYWAY_BASELINE_ON_MIGRATE="false"
$env:APP_MCP_ENABLED="false"
cd my-chat-server
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.useTestClasspath=true"
```

3. 启动成功后查询：

```sql
SELECT installed_rank, version, description, type, success
FROM flyway_schema_history
ORDER BY installed_rank;

SELECT to_regclass('public.users') AS common_table,
       to_regclass('public.market_forecast') AS market_table,
       to_regclass('public.paper_account') AS paper_table,
       to_regclass('public.flyway_smoke_test') AS smoke_table;

SELECT * FROM flyway_smoke_test;
```

预期历史中依次出现 V1–V14 与测试 V15，四个代表表均存在，冒烟表包含 `flyway-v4-applied`。停止后再次执行相同启动命令，历史记录数量应保持不变。

#### 5.2 验证已有库接管

1. 备份当前数据库并恢复为独立副本 `my_chat_flyway_existing`，确认副本没有 `flyway_schema_history`。
2. 指向副本，并只在首次启动时开启 baseline：

```powershell
$env:SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/my_chat_flyway_existing?currentSchema=public"
$env:SPRING_FLYWAY_LOCATIONS="classpath:db/migration,classpath:db/flyway-test"
$env:FLYWAY_BASELINE_ON_MIGRATE="true"
$env:APP_MCP_ENABLED="false"
cd my-chat-server
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.useTestClasspath=true"
```

3. 查询 `flyway_schema_history`：预期 version 3 是 `BASELINE`，随后执行 V4–V15；V1–V3 不应重复建表。
4. 停止应用，执行 `Remove-Item Env:FLYWAY_BASELINE_ON_MIGRATE`，再次启动并确认没有重复迁移。
5. 验证完成后删除两个独立测试数据库，并清除 `SPRING_DATASOURCE_URL`、`SPRING_FLYWAY_LOCATIONS` 等测试环境变量。

> 注：测试迁移文件编号仍是 `V4`，与业务库已有的 V4 重名；只在独立测试库 + 显式测试 location 下使用。

---

## API 概览

REST 多数返回 `Result<T> { code, message, data }`（`code === 200` 成功）。**主聊天流**和 **Jobs SSE** 不包这层信封。

| 前缀 | 说明 |
|------|------|
| `POST /ai/normalChat/chat?format=ndjson` | 主 Agent 流式回合（FormData：`prompt`、`chatId`，可选 `files` / `kbId` / `qualityLoop` / `criteria`） |
| `/ai/history/*` | 会话与历史（含时间线 parts） |
| `/ai/knowledge-base/*` | 知识库 CRUD、批量入库、召回测试、分段、重新向量化 |
| `/ai/file/workspace/*` | 工作区浏览与文件操作 |
| `/ai/jobs/active`、`GET /ai/jobs/events` | 异步任务查询与 SSE 通知 |
| `/ai/model/*` | 对话模型管理（列表 / 供应商 / 测试 / 设默认） |
| `/ai/settings/mcp-tools` | MCP / 搜索连接运行时开关 |
| `/ai/apps/market/*` | 股市分析（行情、预测、策略、自选、股灾风险、指数 PE） |
| `/ai/apps/paper-trade/*` | 模拟炒股（账户 / 持仓 / 下单 / AI 轮次 / 研究 / 定时 / 工作流）；`/admin/*` 为历史表 CRUD 与 SQL 导出 |

> 旧的 `POST /ai/agent/runtime`、`POST /ai/agent/evaluate-optimize` 调试入口与 `controller/demo/` 已删除；质量环改由主聊天 `qualityLoop` 参数触发。

---

## 开发路线图

底座 Agent 已可用；后续以 RAG 实现与优化为主线，其余为自用扩展。

- [x] 自循环 Orchestrator-Workers 主聊天 + 回合流水线
- [x] 通用 Agent Runtime：场景 Profile、`agent_run` 持久化、审批记录、指标
- [x] RAG：上传切片、库隔离检索、引用、召回测试、分段预览、异步入库
- [x] 工作区 FileTools + ShellTool + 会话级目录
- [x] NDJSON 时间线回放；模型可配置切换
- [x] MCP（数据库 / 爬虫 / Smithery）+ 运行时开关；联网搜索兜底
- [x] 大厅可挂应用；模拟炒股（账本 / 研究 / AI 操盘 / 五条定时工作流 / 评估邮件）
- [x] 部署：Compose + Jenkins + 容器日志 / 查库
- [ ] **重点：强化 RAG**（父子分段、Rerank、混合检索、库统计、引用与评测体验）
- [ ] 会话级 plan / 可暂停长任务
- [ ] 多用户与权限（当前单机单用户）
- [ ] SSE 断线任务补偿 / 任务取消

迭代条目详见 [docs/迭代规划.md](docs/迭代规划.md)；Agent Runtime 规范见 [docs/Agent Runtime运维与Profile规范.md](docs/Agent%20Runtime运维与Profile规范.md)。

---

## 许可证

MIT License. Copyright (c) 2026 All1217

详见 [LICENSE](LICENSE)。
