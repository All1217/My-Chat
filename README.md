# My-Chat · 可编程自循环 Agent 平台

[![Java](https://img.shields.io/badge/Java-25-orange?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-green?logo=springboot)](https://spring.io/projects/spring-boot)
[![Vue 3](https://img.shields.io/badge/Vue%203-3.5-brightgreen?logo=vue.js)](https://vuejs.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-42.7-blue?logo=postgresql)](https://www.postgresql.org/)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-2.0-purple?logo=spring)](https://spring.io/projects/spring-ai)

---

## 项目介绍

My-Chat 是一套基于 **Vue 3 + Spring Boot + Spring AI** 的通用 Agent 工具：围绕**自循环编排**（Orchestrator-Workers）把知识库、工作区文件、联网搜索和闲聊接成同一条主聊天链路，并可用大厅功能包挂上多种应用。

- **自循环 Agent**：用户一句 → 领班决定下一步 → 工人执行 → 观察再决策，直到收工；写盘后可跑质量环。
- **可编程**：回合是显式流水线（`ChatTurnStage`）；加长业务流程 = 新一站插进列表。大厅应用按清单登记，不必改大厅页。
- **可 RAG**：知识库隔离检索（目录总览 / 向量召回）、引用回显、异步入库；主聊天里由 `retrieve_kb` Worker 调用。
- **可挂应用**：大厅卡片驱动；已有角色扮演、股市分析；HTTP 约定 `/ai/apps/<id>`。

**近期重点是强化 RAG 知识库应用**（召回质量、分段与引用体验、入库与评测），Agent 主循环与挂载应用保持可扩展，但不作为当前主攻。

项目地址：[https://github.com/All1217/My-Chat](https://github.com/All1217/My-Chat)  
克隆：`git clone https://github.com/All1217/My-Chat.git`

通俗架构说明见 [docs/迭代规划/P2-10-agents开发-主聊天技术拆解.md](docs/迭代规划/P2-10-agents开发-主聊天技术拆解.md)。

---

## 主聊天怎么跑

```text
网页发送
  → ChatController（校验 ndjson、绑工作区 / kbId、处理附件）
  → ChatTurnPipeline 五站
        emit_route → load_dialogue → orchestrate → stream_final → quality_loop
  → ChatTurnFinalizer（成功写 Memory + done；轨迹写入 chat_assistant_turns）
```

第 3 站 `orchestrate` 内是自循环：`decideNext` → `retrieve_kb` / `file` / `search` / `general` → 观察，最多数步后 `finish`。传输是 **NDJSON**（`application/x-ndjson`），不是经典 SSE；前端边收边画 route / step / text_delta / 引用。

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
| PDFBox / Apache POI / jsoup | — | PDF / Office / HTML 解析 |

默认对话模型走 DeepSeek（OpenAI 兼容），向量走阿里云 MaaS `text-embedding-v4`（1536 维）。设置里可配置并切换对话模型。

### 前端

| 技术 | 版本 | 说明 |
|------|------|------|
| Vue 3 | 3.5.x | UI |
| TypeScript | 5.6.x | 类型 |
| Vite | 6.x | 构建；`/rag` 代理到 `:8100` |
| Element Plus | 2.9.x | 组件库 |
| Pinia | 2.3.x | 状态 |
| ECharts | 6.x | 股市等应用图表 |

---

## 功能清单

### Agent 主聊天

- 多步编排：知识库 / 文件 / 搜索 / 闲聊 Worker 接力，时间线可回放
- 流式 NDJSON；思考增量、工具调用、KB 引用标签
- 会话绑定 `kbId`、`workDir`；滚动摘要 + 近期原文窗口
- 聊天附件 txt / md / pdf（正文只进本轮，Memory 只留文件名 + 原问）；图片前后端双拒
- 写盘后可选 Evaluator-Optimizer 质量环（`qualityLoop`，默认开）

### RAG 知识库（近期强化方向）

- 知识库 CRUD；按库隔离的向量检索；`catalog` / `vector` 两种范围
- 批量上传（PDF / DOCX / XLSX / HTML / TXT / MD），异步 Job 切片 + 向量化
- 库级切分 / topK / 相似度阈值；文档重新向量化；只读分段预览
- 召回测试（只检索、不生成）；聊天里带来源 filename / score / 摘录
- 入库进度走通知中心（SSE `/ai/jobs/events`）

尚未做、计划贴近知识库的：父子分段、Rerank、混合检索、库统计仪表盘。见 [docs/迭代规划/迭代规划.md](docs/迭代规划/迭代规划.md)。

### 工作区

- 会话级工作目录；目录树 / 预览 / 增删改 / 导入
- Agent `FileTools`：ls、tree、cat、grep、write、mkdir、rm、mv、cp
- 目录选择器 + 安全校验（拒绝系统关键路径）

### 可挂应用

大厅 [`apps/registry.ts`](my-chat-vue3/src/apps/registry.ts) 登记即可上卡；后端放 `com.mychat.apps.<id>`。

| 应用 | 说明 |
|------|------|
| 即刻聊天 | 主 Agent 会话 |
| 知识库管理 | 库 / 文档 / 召回测试 / 分段 |
| 角色扮演 | 情景互动（独立页） |
| 股市分析 | 行情、AI 推演、自选与策略 |
| 设置 | 模型、工作区、Prompt、角色 |

### 其它

- MCP：本地天气、Smithery 工具箱；单连接失败不拖垮启动；联网搜索优先本机 `searchWeb`（Exa → Bocha → Tavily → DuckDuckGo）
- 模型管理：列表 / 供应商 / 测试连通 / 设默认

---

## 项目结构

```
my-chat-server/
└── src/main/java/com/mychat/
    ├── controller/           # 主聊天、历史、知识库、工作区、任务、模型
    ├── controller/demo/      # Agent 调试：/ai/agent/route|orchestrate|evaluate-optimize
    ├── service/agent/        # 编排循环、Worker、质量环
    ├── service/agent/pipeline/  # 回合流水线 ChatTurnStage
    ├── service/knowledge/    # RAG 入库 / 检索 / 分段
    ├── service/chat/         # 会话、Memory、时间线回合
    ├── apps/                 # 大厅功能包（如 market）
    ├── job/                  # 异步任务 + SSE 通知
    ├── tools/                # FileTools、WebSearchTools
    └── config/               # ChatClient、MCP、工作区上下文

my-chat-vue3/src/
    ├── views/                # 聊天、知识库、设置、大厅骨架
    ├── apps/                 # 可挂应用（registry + roleplay + market）
    ├── components/           # ChatBox、时间线、KB 引用、目录选择器
    └── utils/streamChat.ts   # NDJSON 流式客户端

docs/迭代规划/                 # 规划与主聊天架构说明
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

### 2. 初始化数据库

新主机确认 PostgreSQL 已安装 **pgvector** 后，对 `application.yaml` 里的库执行一次 [`schema.sql`](my-chat-server/src/main/resources/schema.sql)（可重复执行）：

```bash
psql -U postgres -d postgres -f my-chat-server/src/main/resources/schema.sql
```

默认连本机 `postgres` 库的 `public` schema。对话模型种子由后端首次启动写入，不必手插。

### 3. 启动后端

```bash
cd my-chat-server
.\mvnw.cmd spring-boot:run
```

`http://localhost:8100`。启动时会准备 `app.workspace.root`。MCP 为惰性握手，远端 404 不会阻止启动。

### 4. 启动前端

```bash
cd my-chat-vue3
npm install
npm run dev
```

`http://localhost:5173`，API 经 `/rag` 转到 8100。

---

## API 概览

REST 多数返回 `Result<T> { code, message, data }`（`code === 200` 成功）。**主聊天流**和 **Jobs SSE** 不包这层信封。

| 前缀 | 说明 |
|------|------|
| `POST /ai/normalChat/chat?format=ndjson` | 主 Agent 流式回合（FormData：`prompt`、`chatId`、可选 `files` / `kbId` / `qualityLoop`） |
| `/ai/history/*` | 会话与历史（含时间线 parts） |
| `/ai/knowledge-base/*` | 知识库 CRUD、批量入库、召回测试、分段、重新向量化 |
| `/ai/file/workspace/*` | 工作区浏览与文件操作 |
| `/ai/jobs/active`、`GET /ai/jobs/events` | 异步任务查询与 SSE 通知 |
| `/ai/model/*` | 对话模型管理 |
| `/ai/apps/market/*` | 股市应用 |
| `POST /ai/agent/route` 等 | 编排调试（非主路） |

---

## 开发路线图

- [x] 自循环 Orchestrator-Workers 主聊天 + 回合流水线
- [x] RAG：上传切片、库隔离检索、引用、召回测试、异步入库
- [x] 工作区 FileTools + 会话级目录
- [x] NDJSON 时间线回放；模型可配置切换
- [x] MCP / 本机搜索兜底；大厅可挂应用（角色扮演、股市）
- [ ] **近期：强化 RAG**（父子分段、Rerank、混合检索、库统计、引用与评测体验）
- [ ] 会话级 plan / 可暂停长任务
- [ ] Docker 一键部署

迭代条目详见 [docs/迭代规划/迭代规划.md](docs/迭代规划/迭代规划.md)；Agent 规划见 [P2-10-agents开发规划（v1.1）](docs/迭代规划/P2-10-agents开发规划（v1.1）.md)。

---

## 许可证

MIT License. Copyright (c) 2026 All1217

详见 [LICENSE](LICENSE)。
