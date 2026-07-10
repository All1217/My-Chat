# My-Chat · 智能对话与知识库系统

[![Java](https://img.shields.io/badge/Java-25-orange?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-green?logo=springboot)](https://spring.io/projects/spring-boot)
[![Vue 3](https://img.shields.io/badge/Vue%203-3.5-brightgreen?logo=vue.js)](https://vuejs.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-42.7-blue?logo=postgresql)](https://www.postgresql.org/)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-2.0-purple?logo=spring)](https://spring.io/projects/spring-ai)

---

## 📖 项目介绍

My-Chat 是一个基于 **Vue 3 + Spring Boot + Spring AI** 的智能对话与知识库辅助系统，提供多轮对话、RAG 知识库问答和文件管理功能。

- **普通对话模块**：与 AI 进行多轮对话，支持上下文记忆和会话持久化。
- **RAG 知识库模块**：基于文档上传与向量检索，让 AI 能够根据用户文档内容进行精准问答。

项目地址：[https://github.com/All1217/My-Chat](https://github.com/All1217/My-Chat)  
克隆地址：`git clone https://github.com/All1217/My-Chat.git`  

---

## 🧱 技术栈

### 后端

| 技术 | 版本 | 说明 |
|------|------|------|
| Java | 25 | 开发语言 |
| Spring Boot | 4.1.0 | 应用框架 |
| Spring AI | 2.0.0 | AI 集成框架（支持 OpenAI 兼容模型） |
| MyBatis-Plus | 3.5.15 | ORM 框架 |
| PostgreSQL | 42.7.3 | 关系数据库（需 pgvector 扩展） |
| PDFBox | 2.0.31 | PDF 文档解析 |
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

---

## 🔧 功能特性

### ✅ 已实现

- **普通对话模块**：与 AI 进行多轮对话，支持上下文记忆。
- **对话历史管理**：创建、查看、删除会话，历史消息持久化存储。
- **会话内存持久化**：基于 JDBC 的 Spring AI ChatMemory，对话状态不丢失。
- **RAG 知识库问答**：文档上传 → 向量化 → 语义检索，让 AI 基于文档内容精准回答。
- **多格式文档解析**：支持 PDF、Word（docx）、Excel（xlsx）等格式上传与解析。
- **文件扫描工具（ShellTool）**：扫描本地目录文件，提取内容辅助对话。
- **文件管理**：工作区文件浏览、预览与管理。
- **设置面板**：多模型切换、Prompt 管理、角色管理、工作区配置。

### 🚧 研发中

- **知识库管理页面**：上传、分类、检索文档，构建个人知识库。
- **全自动网络数据采集**
- **AI 角色扮演**

---

## 📂 项目结构

```
my-chat-server/               # 后端代码
├── pom.xml
├── src/main/java/com/mychat/
│   ├── Application.java      # 启动类
│   ├── common/               # 通用响应、枚举
│   │   └── result/           # Result 统一返回格式
│   ├── config/               # AI 配置、MVC 配置
│   ├── controller/           # REST 接口控制器
│   ├── debug/                # 调试辅助（日志、配置探测）
│   ├── entity/               # 实体类（PO、DTO、VO）
│   ├── mapper/               # MyBatis-Plus Mapper
│   ├── service/              # 业务逻辑层
│   ├── tools/                # 工具类（ShellTool 等）
│   └── utils/                # 通用工具
└── src/main/resources/       # 配置文件、SQL 等

my-chat-vue3/                 # 前端代码
├── package.json
├── vite.config.ts
├── src/
│   ├── api/                  # 接口请求
│   ├── components/           # 公共组件（ChatBox、MarkdownRenderer 等）
│   ├── views/                # 页面视图（聊天、知识库、设置等）
│   ├── router/               # 路由配置
│   ├── stores/               # 状态管理（Pinia）
│   ├── types/                # TypeScript 类型定义
│   ├── utils/                # 工具（HTTP 封装、流式聊天等）
│   └── assets/               # 静态资源
├── docs/                     # 开发文档
└── README.md
```

---

## 🚀 快速开始

### 环境要求

| 依赖 | 版本 | 必需 | 说明 |
|------|------|------|------|
| JDK | 25 | 是 | 后端运行环境 |
| Node.js | 18+ | 是 | 前端构建 |
| Maven | 3.6+ | 是 | 后端构建工具 |
| PostgreSQL | 14+ | 是 | 数据库（需 pgvector 扩展） |

### 1. 克隆项目

```bash
git clone https://github.com/All1217/My-Chat.git
cd My-Chat
```

### 2. 设置环境变量

| 变量 | 用途 |
|------|------|
| `PGSQL_PASS` | PostgreSQL 密码 |
| `OPENAI_API_KEY` | DeepSeek / OpenAI 兼容模型 API Key |
| `EMBEDDING_MODEL_API_KEY` | 阿里云 MaaS 向量模型 API Key |

### 3. 启动后端

```bash
cd my-chat-server
.\mvnw.cmd spring-boot:run
```

服务默认启动于 `http://localhost:8100`。

### 4. 启动前端

```bash
cd my-chat-vue3
npm install
npm run dev
```

前端服务默认启动于 `http://localhost:5173`。

---

## 📸 效果展示

> 待补充截图

---

## 🗺️ 开发路线图

- [x] 基础对话模块（上下文记忆、会话管理）
- [x] RAG 知识库功能（文档上传、向量化、语义检索）
- [x] 多格式文档解析（PDF、Word、Excel）
- [x] 文件扫描工具（ShellTool）
- [x] 文件管理（工作区浏览）
- [x] 设置面板（模型、Prompt、角色、工作区配置）
- [ ] 知识库管理页面（上传、分类、检索）
- [ ] 多模型切换支持
- [ ] Docker 一键部署
- [ ] AI 角色扮演
- [ ] 全自动网络数据采集

---

## 🤝 如何贡献

欢迎提交 Issue 和 Pull Request！  
如果你有好的建议或发现了 Bug，请通过 [GitHub Issues](https://github.com/All1217/My-Chat/issues) 反馈。

---

## 📄 许可证

本项目采用 MIT License。  
Copyright (c) 2024 All1217

MIT 是一种宽松的开源协议，允许任何人自由使用、复制、修改、合并、发布、再许可和/或销售本软件的副本，只需在软件的所有副本或重要部分中保留上述版权声明和许可声明即可。详见 [LICENSE](LICENSE) 文件。
