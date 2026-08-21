# My-Chat · 智能对话与知识库系统

[![Java](https://img.shields.io/badge/Java-25-orange?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-green?logo=springboot)](https://spring.io/projects/spring-boot)
[![Vue 3](https://img.shields.io/badge/Vue%203-3.5-brightgreen?logo=vue.js)](https://vuejs.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-42.7-blue?logo=postgresql)](https://www.postgresql.org/)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-2.0-purple?logo=spring)](https://spring.io/projects/spring-ai)

---

## 项目介绍

My-Chat 是一个基于 **Vue 3 + Spring Boot + Spring AI** 的智能对话与知识库辅助系统，提供多轮对话、RAG 知识库问答和 AI 文件操作能力。

- **普通对话**：与 AI 进行多轮对话，支持会话持久化、自定义工作目录、AI 文件读写操作
- **RAG 知识库**：文档上传 → 向量化 → 语义检索，让 AI 基于用户文档内容精准问答
- **文件工作区**：支持切换服务端工作目录，AI 具备 ls / tree / cat / grep / write / mkdir 等文件操作能力

项目地址：[https://github.com/All1217/My-Chat](https://github.com/All1217/My-Chat)  
克隆地址：`git clone https://github.com/All1217/My-Chat.git`

---

## 技术栈

### 后端

| 技术 | 版本 | 说明 |
|------|------|------|
| Java | 25 | 开发语言 |
| Spring Boot | 4.1.0 | 应用框架 |
| Spring AI | 2.0.0 | AI 集成框架（OpenAI 兼容） |
| MyBatis-Plus | 3.5.15 | ORM 框架 |
| PostgreSQL | 42.7.3 | 关系数据库（需 pgvector 扩展） |
| PDFBox | 2.0.31 | PDF 文档解析 |
| Apache POI | 5.4.0 | DOCX / XLSX 文档解析 |
| Lombok | - | 代码简化 |

### 前端

| 技术 | 版本 | 说明 |
|------|------|------|
| Vue 3 | 3.5.x | 前端 UI 框架 |
| TypeScript | 5.6.x | 开发语言 |
| Vite | 6.x | 构建工具 |
| Element Plus | 2.9.x | UI 组件库 |
| Pinia | 2.3.x | 状态管理 |
| Vue Router | 4.5.x | 路由 |
| Less | 4.x | CSS 预处理器 |

---

## 功能清单

### 对话

- 多轮上下文对话，支持会话创建 / 重命名 / 删除
- 流式响应（SSE），支持思考链渲染 `[THINKING]...[/THINKING]`
- 自定义工作目录：AI 可读取、创建、修改指定服务端目录的文件
- RAG 知识库问答：上传文档 → 向量化 → 基于语义检索的精准回答

### 工作区文件管理

- 目录树浏览（懒加载）、文件列表、文本/二进制预览
- 新建 / 重命名 / 删除文件或目录
- 上传、导入文件
- **目录选择器**：支持手动输入路径、目录浏览器弹窗（列出服务端磁盘根目录及子目录）、联想搜索（`el-autocomplete` + 防抖）
- 安全校验：拒绝将 `C:\Windows`、`/etc` 等系统关键目录设置为工作区

### 知识库

- 知识库 CRUD
- 文档上传（PDF / DOCX / XLSX / TXT 等）
- 自动切片、向量化存储
- 按知识库隔离的 RAG 检索

### 设置面板

- 模型管理
- 工作区管理（完整文件浏览器）
- Prompt 模板管理
- 角色管理

---

## 项目结构

```
my-chat-server/                 # 后端项目
├── pom.xml
└── src/main/java/com/mychat/
    ├── Application.java        # 启动类
    ├── common/result/          # Result 统一返回格式
    ├── config/                 # AI 配置、MVC、WorkspaceContext
    ├── controller/             # REST 控制器
    │   ├── ChatController         # /ai/normalChat/chat（含知识库：可选 kbId）
    │   ├── ChatHistoryController  # /ai/history/*
    │   ├── FileController         # /ai/file/*
    │   └── KnowledgeBaseController# /ai/knowledge-base/*
    ├── entity/                 # 实体类
    ├── mapper/                 # MyBatis-Plus Mapper
    ├── service/                # 业务逻辑
    ├── tools/                  # AI 工具（FileTools, ShellTool(已废弃)）
    └── utils/                  # WorkspaceUtil 等

my-chat-vue3/                   # 前端项目
├── package.json
├── vite.config.ts
└── src/
    ├── api/                    # API 模块（chat, knowledge, workspace）
    ├── components/             # 公共组件
    │   ├── ChatBox.vue             # 消息列表 & 流式渲染
    │   ├── ChatList.vue            # 侧边栏会话列表
    │   ├── DirectoryPicker.vue     # 服务端目录浏览器弹窗
    │   └── MarkdownRenderer.vue    # Markdown 渲染
    ├── views/                  # 页面视图
    │   ├── ChatView.vue
    │   ├── HomeView.vue
    │   ├── AboutView.vue
    │   ├── LobbyView.vue
    │   ├── knowledgeStore/     # 知识库管理
    │   └── settings/           # 设置（model, workspace, prompt, role）
    ├── router/                 # 路由配置
    ├── stores/                 # Pinia 状态管理（useChatStore）
    ├── types/                  # TypeScript 类型定义
    ├── utils/                  # HTTP 封装、流式聊天
    └── assets/                 # 静态资源

docs/                           # 开发文档与迭代规划
```

---

## 快速开始

### 环境要求

| 依赖 | 版本 | 必需 | 说明 |
|------|------|------|------|
| JDK | 25 | 是 | 后端运行环境 |
| Node.js | 18+ | 是 | 前端构建 |
| Maven | 3.6+ | 是 | 后端构建工具 |
| PostgreSQL | 14+ | 是 | 数据库（需 pgvector 扩展） |

### 1. 设置环境变量

| 变量 | 用途 |
|------|------|
| `PGSQL_PASS` | PostgreSQL 密码 |
| `OPENAI_API_KEY` | DeepSeek chat model API Key |
| `EMBEDDING_MODEL_API_KEY` | 阿里云 MaaS 向量模型 API Key |

### 2. 初始化数据库

执行 `my-chat-server/src/main/resources/schema.sql` 中的 PostgreSQL 部分。

### 3. 启动后端

```bash
cd my-chat-server
.\mvnw.cmd spring-boot:run
```

服务启动于 `http://localhost:8100`。启动时自动校验并创建 `app.workspace.root` 目录。

### 4. 启动前端

```bash
cd my-chat-vue3
npm install
npm run dev
```

前端服务启动于 `http://localhost:5173`。

---

## API 概览

所有后端端点位于 `/ai/*`，统一返回 `Result<T> { code, message, data }`（code 200 = 成功）。

| 控制器 | 端点 | 说明 |
|--------|------|------|
| ChatController | `POST /ai/normalChat/chat` | 流式对话（FormData: prompt + chatId + files + 可选 kbId；`format=ndjson`） |
| ChatHistoryController | `GET/POST/DELETE /ai/history/*` | 会话 CRUD |
| FileController | `GET /ai/file/workspace/tree` | 工作区目录树 |
| FileController | `GET /ai/file/workspace/tree/lazy` | 懒加载子节点 |
| FileController | `GET /ai/file/workspace/list` | 目录文件列表 |
| FileController | `GET /ai/file/workspace/read` | 读文本文件 |
| FileController | `GET /ai/file/workspace/read/binary` | 读二进制文件(Base64) |
| FileController | `POST /ai/file/workspace/folder` | 新建文件夹 |
| FileController | `POST /ai/file/workspace/delete` | 删除文件/目录 |
| FileController | `POST /ai/file/workspace/rename` | 重命名 |
| FileController | `POST /ai/file/workspace/switch` | 切换工作目录 |
| FileController | `POST /ai/file/workspace/import` | 导入文件 |
| FileController | `GET /ai/file/workspace/roots` | 列出文件系统根目录 |
| FileController | `GET /ai/file/workspace/browse` | 浏览绝对路径子目录 |
| FileController | `GET /ai/file/workspace/suggest` | 目录联想搜索 |
| FileController | `GET /ai/file/workspace/validate` | 校验工作目录合法性 |
| KnowledgeBaseController | `GET/POST/DELETE /ai/knowledge-base/*` | 知识库 CRUD |

---

## 开发路线图

- [x] 基础对话模块（上下文记忆、会话管理）
- [x] RAG 知识库（文档上传、向量化、语义检索）
- [x] 多格式文档解析（PDF、Word、Excel）
- [x] AI 文件操作工具（FileTools: ls/tree/cat/grep/write/mkdir/rm/mv/cp）
- [x] 自定义工作目录（手动输入 + 目录浏览器 + 联想搜索 + 安全校验）
- [x] 流式响应 + 思考链渲染
- [x] 知识库管理页面（创建、删除、文档上传与检索）
- [x] 设置面板（模型、Prompt、角色、工作区配置）
- [ ] 多模型动态切换
- [ ] Docker 一键部署
- [ ] AI 角色扮演
- [ ] 全自动网络数据采集

---

## 许可证

MIT License. Copyright (c) 2024 All1217

详见 [LICENSE](LICENSE) 文件。
