# AI 工作区上下文（Workspace Context）— 技术分析

> 专业术语：**AI Workspace Context**（AI 工作区上下文），亦称 **会话级工作目录沙箱**。  
> 对标产品：Cursor / VS Code Copilot 的 Workspace Awareness、Claude Code 的 Project Context。

---

## 一、功能介绍

My-Chat 允许用户为每个对话指定一个服务端磁盘目录作为 **工作区上下文**。进入该对话后，AI 可通过 `FileTools`（一组强类型 `@Tool` 方法）在该目录下执行文件增删改查——类似于在 IDE 中打开一个项目，AI 助手能直接操作该项目的文件。

| 能力 | 说明 |
|------|------|
| `ls` | 列出目录内容（目录优先排序，展示文件名 + 类型 + 大小） |
| `tree` | 递归展示目录树结构 |
| `cat` | 读取文件内容（限 1MB 大小 / 20000 字符输出） |
| `grep` | 在常见文本文件中搜索匹配内容 |
| `write` | 创建或覆盖写入文本文件（自动创建父目录） |
| `mkdir` | 创建目录（含父目录） |
| `rm` | 删除文件或递归删除目录 |
| `mv` | 移动 / 重命名（跨目录用 `Files.move` 兜底） |
| `cp` | 复制文件或目录（递归） |
| `stat` | 查看文件 / 目录元信息 |

---

## 二、业务流程

### 2.1 完整链路

```
用户操作                     前端                          后端                          AI 模型
─────────                   ─────                        ─────                         ────────
点击"打开目录"      →   弹出目录弹窗
                      ┌─ 手动输入路径
                      ├─ 点击文件夹图标
                      │    → DirectoryPicker            → GET /workspace/roots
                      │    → 浏览子目录                  → GET /workspace/browse
                      └─ 打字触发联想
                           → el-autocomplete             → GET /workspace/suggest
                           (300ms 防抖)

点击"确定"          →   validate(path)                  → GET /workspace/validate
                          ↓ 校验通过                         ├─ 路径存在性
                      createConversation(id, workDir)    → POST /ai/history/add        → INSERT chat_sessions
                          workDir 存入 chat_sessions.work_dir

发送消息             →   fetch POST /ai/normalChat/chat
                           (FormData: prompt + chatId)   → ChatController.chat()
                                                             ├─ getWorkDir(chatId)  → SELECT work_dir FROM chat_sessions
                                                             ├─ WorkspaceContext.set(workDir)
                                                             ├─ buildWorkspaceSystemPrompt()
                                                             │    "当前工作目录: D:/projects/my-app
                                                             │     路径规则：所有路径都是相对于当前工作目录的**相对路径**。"
                                                             └─ toolChatClient.prompt().system(...).stream()
                                                                                               ↓
                                                                                          AI 调用 FileTools
                                                                                          (ls / cat / write ...)
                                                                                               ↓
                                                                                          WorkspaceUtil.resolveSafe()
                                                                                          → Java NIO 操作文件
```

### 2.2 三种目录输入方式的对比

| 方式 | 组件 | 后端端点 | 特点 |
|------|------|---------|------|
| 手动输入 | `el-autocomplete` 文本框 | —（验证时调用 validate） | 直接输入路径字符串 |
| 目录浏览器 | `DirectoryPicker.vue` | `/roots` + `/browse` | 面包屑导航，点击展开子目录 |
| 联想搜索 | `el-autocomplete` + 防抖 300ms | `/suggest` | 取父目录列表 + 最后一段前缀过滤 |

三者共用 `handleConfirmDirectory` 入口，统一在确认前调用 `validate` 端点做安全校验。

---

## 三、架构设计

### 3.1 整体架构

```
┌────────────────────────────────┐
│   ChatView.vue / ChatList.vue  │  ← 前端入口
│   DirectoryPicker.vue          │  ← 目录浏览器组件
│   workspaceApi (Axios)         │  ← API 调用层
└──────────────┬─────────────────┘
               │ HTTP /rag/ai/*
               ▼
┌────────────────────────────────┐
│   ChatController               │  ← 流式对话端点
│   FileController               │  ← 文件管理端点
│   ChatHistoryController        │  ← 会话 CRUD
└──────────────┬─────────────────┘
               │
    ┌──────────┼──────────┐
    ▼          ▼          ▼
┌─────────┐ ┌──────────┐ ┌──────────────┐
│Workspace│ │FileTools  │ │WorkspaceUtil │
│Context  │ │(@Tool x9) │ │(Java NIO)    │
│(Thread  │ │           │ │              │
│ Local)  │ │           │ │              │
└─────────┘ └──────────┘ └──────────────┘
    │          │               │
    └──────────┴───────┬───────┘
                       ▼
              ┌────────────────┐
              │   PostgreSQL   │
              │  chat_sessions │  ← work_dir 列
              │  spring_ai_    │
              │  chat_memory   │
              └────────────────┘
```

### 3.2 核心组件详解

#### WorkspaceContext — 线程级工作目录持有者

```java
// 基于 InheritableThreadLocal，设置后当前请求线程及其子线程均可读取
public class WorkspaceContext {
    private static final InheritableThreadLocal<String> WORK_DIR = new InheritableThreadLocal<>();
    public static void set(String workDir) { ... }
    public static String get() { ... }
    public static void clear() { ... }
}
```

**为什么用 ThreadLocal？**  
每个 HTTP 请求由 Tomcat 虚拟线程处理，该线程独有的 `workDir` 无需传递给其他请求。`ChatController.chat()` 在流式处理开始时设置，`doFinally` 中清理，生命周期与请求严格绑定。

**跨线程传播（关键难点）：**  
Tomcat 虚拟线程 → AI 模型调用（Netty 反应式线程）→ `FileTools`（Netty 线程）之间存在线程切换。`WorkspaceContextAccessor` 实现 Micrometer `ThreadLocalAccessor` 接口，配合 `Hooks.enableAutomaticContextPropagation()` 将 ThreadLocal 值自动注入 Reactor Context，确保文件操作能读到正确的工作目录。

#### WorkspaceUtil — 文件操作引擎

```java
private Path getEffectiveRoot() {
    String contextWorkDir = WorkspaceContext.get();
    return (contextWorkDir != null)
            ? Paths.get(contextWorkDir).toAbsolutePath().normalize()
            : workspaceRoot;  // application.yaml → app.workspace.root
}

public Path resolveSafe(String relativePath) {
    Path root = getEffectiveRoot();
    Path resolved = root.resolve(cleaned).normalize();
    if (!resolved.startsWith(root)) {
        throw new SecurityException("非法的路径访问: " + relativePath);
    }
    return resolved;
}
```

**双重安全边界：**
1. `resolveSafe()` — 所有相对路径必须先经此方法解析，确保不逃逸出 `workspaceRoot`（防 `../../../etc/passwd` 路径穿越）
2. `BLOCKED_PATHS` — 禁止将系统关键目录（`C:\Windows`、`/etc`、`/usr` 等）设为工作区根目录

#### FileTools — 强类型参数化工具

不同于旧版 `ShellTool`（单一 `executeCommand(String)` + `split("\\s+")` 字符串解析），`FileTools` 将每个操作拆分为独立 `@Tool` 方法：

```java
@Tool(description = "列出指定目录下的文件和子目录")
public String ls(@ToolParam(description = "目标目录路径，默认当前目录") String path) { ... }

@Tool(description = "查看文件内容")
public String cat(@ToolParam(description = "文件路径") String path) { ... }

@Tool(description = "创建或覆盖写入文本文件")
public String write(
    @ToolParam(description = "文件路径") String path,
    @ToolParam(description = "要写入的文本内容") String content
) { ... }
```

**对比旧版 ShellTool 的改进：**

| 维度 | ShellTool（已废弃） | FileTools（当前） |
|------|-------------------|------------------|
| 参数解析 | `split("\\s+")` 字符串拆分 | Spring AI 自动 JSON 反序列化 |
| 空格文件名 | ❌ 被错误拆分 | ✅ 天然支持 |
| 模型调用准确性 | 需记忆命令格式 `ls <path>` | 只需选工具 + 填参数 |
| `\n` 换行 | ❌ 变成字面量 `\\n` | ✅ 文本正确写入 |
| 通配符 / 管道 | ❌ 模型频繁误用 | ✅ 无法传入（工具语义明确） |

#### buildWorkspaceSystemPrompt — 动态系统提示

```java
private String buildWorkspaceSystemPrompt() {
    String workDir = WorkspaceContext.get();
    String name = Paths.get(workDir).getFileName().toString();
    return String.format("""
            所有涉及文件的查看、创建、写入、修改、删除、重命名、复制操作，都必须通过可用工具实际执行。
            你绝不能在回复中假装已经完成了文件操作。
            
            当前工作目录: %1$s
            路径规则：所有路径都是相对于当前工作目录的**相对路径**。
            不要再把工作目录名 "%2$s" 作为路径前缀。
            ✅ 正确: path="src/components/App.vue"
            ✅ 正确: path="README.md"
            ❌ 错误: path="%2$s/src/components/App.vue"
            ❌ 错误: path="%2$s/README.md"
            """, workDir, name);
}
```

系统提示中明确告知模型：（1）当前工作目录路径；（2）必须使用**相对路径**；（3）不得把目录名当作路径前缀——这是从实际调试中总结出的常见 AI 幻觉模式。

### 3.3 启动时工作目录校验

```java
@PostConstruct
public void init() {
    Path configured = Paths.get(workspaceRootPath).toAbsolutePath().normalize();
    if (Files.exists(configured) && Files.isDirectory(configured) && !isBlockedPath(configured)) {
        this.workspaceRoot = configured;
    } else {
        Path fallback = Paths.get("src/main/resources/workspace").toAbsolutePath().normalize();
        log.warn("配置的工作区目录不可用, 使用默认目录: {}", fallback);
        this.workspaceRoot = fallback;
    }
    Files.createDirectories(this.workspaceRoot);
}
```

配置了 `app.workspace.root` 但目录不存在/是文件/是系统禁止目录时，自动回落内置默认目录，防止启动即不可用。

### 3.4 安全校验矩阵

| 校验点 | 位置 | 校验内容 | 拒绝后的行为 |
|--------|------|---------|------------|
| 启动时 | `WorkspaceUtil.init()` | 配置目录存在 + 是目录 + 非禁止 | 回落 fallback 目录 |
| 确认前 | `GET /workspace/validate` | 路径存在 + 是目录 + 非禁止 | 弹出 toast 提示，阻止会话创建 |
| 浏览时 | `GET /workspace/browse` → `listAbsoluteDirectory()` | 浏览目标 ± 是禁止目录 | 弹出 toast 提示 |
| 联想时 | `GET /workspace/suggest` → `listSubDirectories()` | 过滤结果中的禁止目录 | 不返回禁止目录的联想结果 |
| 切换时 | `POST /workspace/switch` → `switchRoot()` | 目标存在 + 是目录 + 非禁止 | 抛出 SecurityException |
| 文件操作 | `WorkspaceUtil.resolveSafe()` | 解析后路径不逃逸 workRoot | 抛出 SecurityException |

---

## 四、技术亮点

### 4.1 三级目录选择器

不同于简单的路径输入框，提供了 **手动输入 + 图形浏览器 + 防抖联想** 三种互补方式，用户无需记住完整路径即可快速定位目标目录。目录浏览器展示服务端磁盘根目录列表，支持面包屑导航和层级浏览。

### 4.2 跨线程上下文传播

通过 Micrometer ContextPropagation + Reactor Hooks，将 ThreadLocal 中的工作目录自动注入 Netty 反应式线程，解决了 "HTTP 线程设值 → AI 模型异步调用 → Netty 线程取不到值" 的经典问题。无需显式传递参数。

### 4.3 从 ShellTool 到 FileTools 的重构

这是借鉴 Cursor / Claude Code 设计理念的关键改进。将单一 `executeCommand(String)` 重构为 9 个强类型 `@Tool(description=...)` + `@ToolParam(description=...)` 方法后：
- 空格文件名、中文路径、特殊字符不再被错误拆分
- AI 不再尝试 Unix shell 语法（`ls -la`、`rm *.txt`、`|` 管道）
- 工具调用准确率大幅提升

### 4.4 多层次安全防护

不在单一位置做校验，而是在 **启动、输入、浏览、联想、切换、操作** 六个环节各自把关，即使某一环被绕过，其他环仍能兜底。系统禁止目录黑名单统一管理，前后端双重覆盖。

---

## 五、与成熟产品的对比及待改进点

以 **Cursor**（当前 Agentic IDE 的标杆）为参照，分析差距和改进方向。

### 5.1 已对齐的能力

| 能力 | My-Chat 实现 | Cursor | 状态 |
|------|-------------|--------|------|
| 会话级工作目录 | ThreadLocal + DB 持久化 | 打开项目即上下文 | ✅ |
| 文件 CRUD 工具 | FileTools (9 methods) | Agent tools | ✅ |
| 路径安全校验 | 6 层校验矩阵 | 工作区沙箱 | ✅ |
| 多方式目录选择 | 手动 + 浏览 + 联想 | 文件树 + 最近项目 | ✅ |
| 流式响应 + 思考链 | SSE + [THINKING] 标签 | Streaming with thinking | ✅ |

### 5.2 关键差距和改进方向

#### 差距 1：无代码文件上下文注入（最核心差距）

**现状**：AI 需要主动调用 `ls` / `cat` 才能了解工作目录中有什么文件。每次对话都需要反复探索目录结构，效率较低。

**Cursor 的做法**：打开项目时自动扫描目录结构，将关键文件（`package.json`、`tsconfig.json`、入口文件等）注入 system prompt 作为初始上下文。AI 无需工具调用即了解项目骨架。

**改进方向**：在 `buildWorkspaceSystemPrompt()` 中增加目录摘要注入。启动对话时自动执行 `tree --max-depth=2`，将前 500 行结果附加到系统提示中。

#### 差距 2：文件变更感知缺失

**现状**：AI 通过 `ls` 只能看到文件名和大小，无法判断最近修改了哪些文件、哪些文件是新增的。

**Cursor 的做法**：通过 LSP / Git diff 感知文件变更，AI 能说"你刚才编辑了 `App.vue` 的第 15 行"。

**改进方向**：
- 利用 Java NIO 的 `WatchService` 监听工作目录文件变更事件
- 在系统提示中注入最近变更文件列表（按 `lastModifiedTime` 排序）
- 支持 `git diff` / `git log --oneline` 作为工具（如果工作目录是 Git 仓库）

#### 差距 3：前端非服务端文件系统

**现状**：`DirectoryPicker` 浏览的是服务端文件系统，用户选择的是**服务端磁盘上的目录**。这与用户直觉中的"打开本地项目"有偏差——类似 IDEA 的打开项目一般在客户端本地。

**Cursor 的做法**：客户端本地文件系统直接暴露给 Agent，无需通过网络 API 做服务端文件操作。

**改进方向**：
- 短期：在前端增加提示"选择的是服务端目录"，区分于本地目录
- 长期：如果部署模式变为客户端 Agent + 服务端 LLM 代理模式，则可利用 File System Access API 直接操作本地文件

#### 差距 4：工具调用无进度反馈

**现状**：AI 调用 `tree` 遍历大目录时，前端无任何进度指示器。大文件 `cat` 被截断后用户不知情。

**Cursor 的做法**：长时间操作显示进度条，被截断的内容有明确提示和"查看更多"入口。

**改进方向**：
- 工具执行超过 2 秒时推送中间状态（WebSocket / SSE 事件）
- 截断内容附带独立查看入口（而非仅文本提示）

#### 差距 5：工作目录历史与快速切换

**现状**：每次需重新输入或浏览选择目录。已用过的目录无历史记录。

**改进方向**：
- 记录最近使用的工作目录列表（可存 DB 或 localStorage）
- 打开目录弹窗中展示"最近使用"快捷入口
- 侧边栏展示工作目录路径，支持点击切换

#### 差距 6：无代码 Lint / 格式化反馈循环

**现状**：AI 通过 `write` 修改文件后，不会自动运行 lint / 格式化。用户需要人工检查。

**Cursor 的做法**：文件变更后自动触发 LSP 诊断，将 lint 错误反馈给 AI，形成自动修复循环。

**改进方向**：
- 在系统提示中增加指令"文件写入后请检查常见错误"
- 支持 `lint` / `format` 工具方法（如运行 `eslint --fix`）
- 在 Agent 循环中增加"操作后验证"步骤

#### 差距 7：工作目录与知识库的割裂

**现状**：工作目录（`work_dir`）和知识库（`kb_id`）是两个独立维度，不能同时激活。

**改进方向**：
- 允许对话同时关联工作目录和知识库
- RAG 检索结果中标注哪些内容来自工作目录文件

---

## 六、与其他功能模块的关联

| 关联模块 | 关联方式 | 影响 |
|----------|---------|------|
| 聊天对话 | `chat_sessions.work_dir` 存储 | 每个会话独立工作目录 |
| RAG 知识库 | `chat_sessions.kb_id` 独立字段 | 当前互斥，后续可共存 |
| 文件管理 | `FileController` 共享 `WorkspaceUtil` | 目录浏览、文件预览共用引擎 |
| 设置面板 | `WorkspaceManagement.vue` 独立入口 | 全局工作区管理 |
| Agent 工具调用 | `FileTools` 依赖 `WorkspaceContext` | 确保每请求隔离 |

---

## 七、关键文件索引

| 文件 | 职责 |
|------|------|
| `ChatView.vue` | "打开目录"入口 + 目录弹窗 + autocomplete + DirectoryPicker 集成 |
| `ChatList.vue` | 侧边栏"打开目录"按钮 + 同上逻辑 |
| `DirectoryPicker.vue` | 服务端目录浏览器弹窗组件 |
| `workspace.ts` | 前端 API：roots / browse / suggest / switch / validate |
| `ChatController.java` | 流式对话：设置 WorkspaceContext + system prompt |
| `WorkspaceContext.java` | ThreadLocal + 跨线程传播 |
| `WorkspaceUtil.java` | Java NIO 文件操作引擎 + 路径安全检查 |
| `FileTools.java` | 9 个 @Tool 方法，AI 模型调用的文件工具集 |
| `AiConfiguration.java` | ChatClient bean 配置 + defaultSystem + defaultTools |
| `ContextPropagationConfiguration.java` | Micrometer 自动上下文传播 |
| `ChatSessionsService.java` | 会话 CRUD，work_dir 存取 |
| `application.yaml` | `app.workspace.root` 配置 |
| `schema.sql` | `chat_sessions.work_dir` 列定义 |
