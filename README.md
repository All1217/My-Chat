# My-Chat · 智能对话与知识库系统

[![Java](https://img.shields.io/badge/Java-17-orange?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-green?logo=springboot)](https://spring.io/projects/spring-boot)
[![Vue 3](https://img.shields.io/badge/Vue%203-3.x-brightgreen?logo=vue.js)](https://vuejs.org/)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-blue?logo=mysql)](https://www.mysql.com/)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-1.1.3-purple?logo=spring)](https://spring.io/projects/spring-ai)

---

## 📖 项目介绍

My-Chat 是一个基于 **Vue 3 + Spring Boot + Spring AI** 的智能对话与知识库辅助系统。当前版本已实现 **普通对话模块**，用户可与大语言模型进行流畅的问答交互，支持对话历史管理和会话持久化。

正在研发中的 **RAG 增强对话模块**，将结合文件扫描工具（`ShellTool`）与向量数据库检索，让 AI 能够基于本地文档内容进行精准回答。两者互补：  
- **文件扫描（ShellTool）**：适合小文件/常规对话增强，直接提取内容作为上下文。  
- **RAG 模块（研发中）**：RAG 模块则更适合大文件/长篇文档，通过分块 + 向量检索，精确定位相关信息，不受文件大小限制。两者互补，覆盖不同场景。

项目地址：[https://github.com/All1217/My-Chat](https://github.com/All1217/My-Chat)  
克隆地址：`git clone https://github.com/All1217/My-Chat.git`  

---

## 🧱 技术栈

### 后端

| 技术 | 版本 | 说明 |
|------|------|------|
| Java | 17 | 开发语言 |
| Spring Boot | 3.5 | 应用框架 |
| Spring AI | 1.1.3 | AI 集成框架（支持 OpenAI 兼容模型） |
| MyBatis-Plus | 3.5.7 | ORM 框架 |
| MySQL | 8.0 | 关系数据库 |
| H2 | 运行时 | 嵌入式测试数据库 |
| Lombok | - | 代码简化 |
| FFmpeg | - | 视频/音频处理工具（工具类） |

### 前端

| 技术 | 版本 | 说明 |
|------|------|------|
| Vue 3 | 3.x | 前端 UI 框架 |
| TypeScript | 5.x | 开发语言 |
| Vite | 5.x | 构建工具 |
| Element Plus / 其他 | 待定 | UI 组件库 |

---

## 🔧 功能特性

### ✅ 已实现
- **普通对话模块**：与 AI 进行多轮对话，支持上下文记忆。
- **对话历史管理**：创建、查看、删除会话，历史消息持久化存储。
- **会话内存持久化**：基于 JDBC 的 Spring AI ChatMemory，对话状态不丢失。
- **文件扫描工具（ShellTool）**：通过 `my-chat-server/src/main/java/com/mychat/tools/ShellTool.java` 扫描本地目录文件，提取内容辅助对话。
- **视频/音频处理工具（FFmpegTool）**：调用 FFmpeg 进行媒体文件处理。

### 🚧 研发中
- **RAG 增强对话**：基于向量数据库（计划使用 pgvector 或类似的向量引擎），实现文档分块、向量化存储和语义检索。
- **大文件支持**：结合 RAG 模块，处理 PDF、Markdown、Word 等格式的长文档。
- **知识库管理**：上传、分类、检索文档，构建个人知识库。
- **全自动网络数据采集**
- **AI角色扮演**

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
│   ├── entity/               # 实体类（PO、DTO、VO）
│   ├── mapper/               # MyBatis-Plus Mapper
│   ├── service/              # 业务逻辑层
│   ├── tools/                # 工具类（ShellTool、FFmpegTool）
│   └── utils/                # 通用工具（FfmpegUtil 等）
└── src/main/resources/       # 配置文件、SQL 等

my-chat-vue3/                 # 前端代码
├── package.json
├── vite.config.ts
├── src/
│   ├── api/                  # 接口请求
│   ├── components/           # 公共组件
│   ├── views/                # 页面视图
│   ├── router/               # 路由配置
│   ├── stores/               # 状态管理（Pinia）
│   └── assets/               # 静态资源
├── docs/                     # 项目文档（架构图、设计稿等）
└── README.md
```

---

## 🚀 快速开始

### 环境要求

| 依赖 | 版本 | 必需 | 说明 |
|------|------|------|------|
| JDK | 17+ | 是 | 后端运行环境 |
| Node.js | 18+ | 是 | 前端构建 |
| Maven | 3.6+ | 是 | 后端构建工具 |
| MySQL | 8.0+ | 是 | 生产数据库 |

> 也可使用 H2 嵌入式数据库快速体验（默认配置），无需额外安装 MySQL。

### 1. 克隆项目

```bash
git clone https://github.com/All1217/My-Chat.git
cd My-Chat
```

### 2. 启动后端

```bash
cd my-chat-server
# 使用 Maven Wrapper（推荐，无需本地安装 Maven）
.\mvnw.cmd spring-boot:run

# 如果已安装 Maven 并配置了环境变量，也可使用：
mvn spring-boot:run
```

服务默认启动于 `http://localhost:8100`。  
Spring AI 需要配置 LLM API Key，推荐在 `application.yml` 中设置：

```yaml
spring:
  ai:
    openai:
      api-key: your-api-key-here
      chat:
        options:
          model: deepseek  # 或其他兼容模型
```

### 3. 启动前端

```bash
cd my-chat-vue3
npm install
npm run dev
```

前端服务默认启动于 `http://localhost:5173`。

---

## 📸 效果展示

> 待补充截图

- 对话聊天界面
- 历史会话列表
- 文件扫描工具执行界面（后台日志）

---

## 🗺️ 开发路线图

- [x] 基础对话模块（上下文记忆、会话管理）
- [x] 文件扫描工具（ShellTool）
- [ ] RAG 知识库功能（文档上传、向量化、语义检索）
- [ ] 知识库管理页面（上传、分类、检索）
- [ ] 多模型切换支持
- [ ] Docker 一键部署
- [ ] 语音/视频处理集成
- [ ] AI角色扮演
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