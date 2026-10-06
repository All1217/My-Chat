项目构建了一个agent系统，并在此基础上着重研究如何搭建适用于复杂生产环境的RAG系统。此外，也会扩展一些自用的功能模块。

# 1 agent业务总览

![](资源/Assets/agent业务总览.png)

从用户进入聊天界面输入提示词按下回车开始，系统内部大概会经历以下流程：构造上下文、调用模型完成工作、整理最终AI回答、由质量监测系统评估该回答是否符合用户需求、向用户展示答案。

每一个流程背后都会牵涉到大量代码，这些代码在逻辑层面可划分为几个模块，比如用户在聊天界面输入提示词命令AI工作，这个涉及到**用户交互模块**；构造上下文涉及到**Prompt工程**和**数据支撑模块**；具体让AI执行工作需要**模型调用模块**，这个模块是比较核心的模块；AI生成回答后，不能直接给用户看，还要把它修改成同时符合用户需求和系统内部规范的形式，这需要用到**输出后处理模块**；此外，AI的回答还需要经过**质量监测模块**审核，以防回答偏离用户需求或是违反法律法规。



# 2 用户交互

项目采用C/S架构，前后端分离。其中前端为了实现流式聊天响应，不用Axios封装的api请求后端接口，而是用fetch：[`streamChat.ts`](../my-chat-vue3/src/utils/streamChat.ts)。

一般和AI聊天采用SSE流式输出，这类业务场景里AI吐出的内容可能很长，把一整段思考内容打印出来，再加上最终的回答，整个过程消耗几十分钟也说不定。所以前端和后端之间需要长连接，让服务器（AI）持续且单向地朝客户端推送信息流。

Axios更适合一次性请求-响应模式，并不原生支持 Server-Sent Events（SSE）这种长连接、流式数据推送协议。axios 基于 `XMLHttpRequest`，而 XHR 没有标准的 `ReadableStream`，虽然可以通过 `onDownloadProgress` 拿到部分 `responseText`，但它是累积文本，不是真正的流，你要自己记录上次读取位置、截取新增内容、处理分块边界、处理 UTF-8 多字节字符被截断、防止响应越长内存越大……总之拔高了开发成本。

而且 axios 的默认 `transformResponse`、拦截器、JSON 解析逻辑，会干扰 `text/event-stream` 这种非一次性 JSON 响应。虽然可以配置 `responseType: 'stream'`开启流式输出，并关闭拦截响应和超时判断，但相当于绕远路，浪费开发成本。

fetch 原生暴露 ReadableStream，适合逐 token 读取。而且 AI 聊天经常需要“停止生成”，`fetch` 可以配合 `AbortController`实现中断流式输出。fetch 还支持 POST、自定义 header、cookie、跨域配置等，比 axios 更灵活、更支持底层自定义。因此在AI聊天场景中使用fetch比axios更常见。

Websocket协议也可实现SSE，但是Websocket是全双工，常规AI聊天仅用半双工（服务器推送到客户端）就够用，Websocket太“重”。

## 2.1 SSE通信

`Project Reactor`：[Project Reactor](https://projectreactor.io/)

**模型总览**

传统同步阻塞：调一个方法，线程卡住等返回。该库采用`发布者-订阅者`模型：

1. 你先描述一条管道：数据从哪来、怎么变、往哪送（`map` / `flatMap` / `doOnNext`…）。
2. 有人订阅之后，管道才真正开始跑。
3. 上游推数据，下游收；下游忙不过来时可以要慢一点（背压），避免被冲垮。

`Mono` / `Flux` 就是这条管道上的两种「发布者」：一个最多一件货，一个可以一串货。

**Flux：多元素发布者**

像**水管**：可以陆续流出 0～很多个值。

主聊天返回 `Flux<String>`：每一行 NDJSON（`route`、`step`、`text_delta`…）就是管子里冒出来的一滴水，前端边收边画。

**Mono：单元素发布者**

像**快递单**：要么送来一件货，要么空着/出错，**不会**连送一串。

例如 `streamFinalAnswer` 整段跑完用 `Mono<Void>` 表示「这件事做完了」；`orchestrate` 包进 `Mono.fromCallable(...)` 表示「异步算出一份编排结果」。

**Sinks：发布者与订阅者之间的管道**

总体来说是管道，但不是纯粹的无状态管道，还携带了消息上限限制。

**背压**

本质是订阅者向发布者的一种反馈机制。下游处理不过来时，能告诉上游「慢一点 / 少推一点」，避免数据把内存或线程冲爆。内部相关代码（写在Sinks管道上）：

```java
Sinks.many().replay().limit(1024) + tryEmitNext
```

本质是带上限的缓冲区，不是严格意义的强背压，超过1024条消息将抛弃，写的时候也是一股脑往管道塞，不会实时根据下游减慢速度。

**订阅者**

最外层订阅者是「把 `Flux<String>` 写成 HTTP 响应」的那一层（Spring 在返回 `Flux` 时帮你订上），再往外是浏览器在读响应体（`fetch` / `ReadableStream` 一行行拉 NDJSON）。

**注意事项**

消息在会话内是总体有序的，依靠发布者推送消息时先来后到保证有序，类似于简单队列，没有其他兜底机制，这意味着会话内若出现多线程场景，可能会导致乱序。尽管每次推送的消息都携带`seq`标号，但仅仅是个顺序标识，实际没利用到。

**为什么选择`Project Reactor`？**

**主聊天在干什么？**

用户发一句 → 后端编排、出步骤、吐正文、可能再跑质量环 → **中间过程要立刻出现在界面上**，不能等全部算完再一次性返回。

这天然是一条**长时间、多段事件流**，不是普通「请求 → 一个 JSON → 结束」。

**为啥 Reactor 合适（通俗版）？**

1. **天生表达「流」**  
   `Flux` 就是「陆续往外冒东西」：一行行 NDJSON（`route` / `step` / `text_delta`）。用回调也能做，但多步串起来容易乱成意大利面。
2. **和 Spring 一条绳上的蚂蚱**  
   你们是 Spring Boot + Spring AI。返回 `Flux`、在管道里 `flatMap` / `doFinally`，是生态里的常规写法，文档和例子都多，不必另起一套异步框架。
3. **取消、收尾有现成钩子**  
   用户断开、出错、正常结束，可以在 `doFinally` 里清工作区、决定写不写 Memory——流式聊天很需要这种「收场逻辑」。

**为啥不（一定）用别的？**

| 方案                  | 也能做流式吗             | 相对你们的代价                                   |
| --------------------- | ------------------------ | ------------------------------------------------ |
| 纯同步 Servlet 阻塞写 | 能，但线程易被长时间占满 | 编排+模型很慢，线程模型更吃力                    |
| 自己回调 / 线程池拼   | 能                       | 组合、取消、错误传递更碎                         |
| WebSocket 自建协议    | 能                       | 前端、网关、重连都要另设计；你们已用 HTTP NDJSON |
| RxJava 等             | 类似模型                 | 不如 Reactor 契合Spring生态                      |

## 2.2 RESTFUL API重构方案

**现状问题**

当前调用链过长，且职责分散：

```mermaid
flowchart LR
  caller["Store/Component"] --> requestFn["request(() => ragHttp.get(...))"]
  requestFn --> methods["http.ts methods"]
  methods --> axios["Axios"]
  axios --> respInterceptor["响应拦截: 脱 Axios 外壳"]
  respInterceptor --> requestTs["request.ts: 校验 code + 解包 data"]
  requestTs --> callerCheck["调用方: if (data) / if (ok)"]
```

主要痛点：

- `request(() => ragHttp.get(...))` 回调嵌套，可读性差
- 错误处理分散在 [http.ts](my-chat-vue3/src/utils/http.ts) 拦截器与 [request.ts](my-chat-vue3/src/utils/request.ts) 两处，且部分调用绕过 `request`（如 [chat.ts L39-45、L64-65](my-chat-vue3/src/stores/chat.ts)）
- `request` / `mutate` / `requestAndSet` 三个包装器语义重叠；`requestAndSet` 未被使用
- `crmHttp` 已导出但全项目无引用

**目标架构**

```mermaid
flowchart LR
  caller["Store/Component"] --> apiLayer["api/chat.ts 等"]
  apiLayer --> client["ragClient.get/post"]
  client --> axios["Axios"]
  axios --> unified["统一拦截器: 脱壳 + 校验 code + 返回 T"]
  unified -->|"code !== 200"| apiError["throw ApiError + toast"]
  unified -->|"code === 200"| data["直接返回 T"]
```

调用对比：

```typescript
// Before
const list = await request(
  () => ragHttp.get<ChatSessionVO[]>('/ai/history/getConversations'),
  { errorMsg: '获取会话列表失败' }
)
if (list) chatList.value = list

// After
try {
  chatList.value = await chatApi.getConversations()
} catch { /* 拦截器已 toast，此处可留空或做降级 */ }
```

---

**新文件结构**

```
my-chat-vue3/src/
  utils/http/
    types.ts      # ApiError、RequestOptions
    client.ts     # HttpClient 类 + ragClient / crmClient
    index.ts      # barrel export
  api/
    chat.ts       # 会话 / 历史消息
    knowledge.ts  # 知识库 + 文档上传
    workspace.ts  # 工作区文件管理
```

删除旧文件：

- [my-chat-vue3/src/utils/http.ts](my-chat-vue3/src/utils/http.ts)
- [my-chat-vue3/src/utils/request.ts](my-chat-vue3/src/utils/request.ts)

保留不动：[streamChat.ts](my-chat-vue3/src/utils/streamChat.ts)（流式 API 本次不重构）

---

**1. `utils/http/types.ts`**

```typescript
/** 与后端 Result.java 对齐 */
export interface ResultData<T = unknown> {
  code: number
  message: string
  data: T
}

export class ApiError extends Error {
  constructor(
    public code: number,
    message: string,
    public silent = false,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

/** 扩展 Axios 请求配置 */
export interface RequestOptions {
  params?: Record<string, unknown>
  /** 不弹出 ElMessage */
  silent?: boolean
  /** 覆盖后端 message 作为提示文案 */
  errorMsg?: string
}
```

---

**2. `utils/http/client.ts`（核心）**

将 Axios 外壳解包 + `Result<T>` 业务解包 + 错误 toast **合并到响应拦截器**，对外方法直接返回 `Promise<T>`：

```typescript
import axios, { type AxiosInstance, type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import { ResultEnum } from '@/types/enums'
import { ApiError, type ResultData, type RequestOptions } from './types'

type Config = AxiosRequestConfig & RequestOptions

class HttpClient {
  private readonly axios: AxiosInstance

  constructor(baseURL: string) {
    this.axios = axios.create({
      baseURL,
      timeout: ResultEnum.TIMEOUT as number,
    })
    this.setupInterceptors()
  }

  private setupInterceptors() {
    this.axios.interceptors.request.use((config) => {
      config.headers['X-Access-Token'] =
        'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VybmFtZSI6IjgwMTAwMzk2IiwiY2xpZW50VHlwZSI6IlBDIiwiZXhwIjoxNzc2OTc2MTU0fQ.CjRzmrPp9hcW3fd5if6kD6htN24gyHiemLaw0r-gx4Y'
      return config
    })

    this.axios.interceptors.response.use(
      (response) => {
        const result = response.data as ResultData
        const opts = response.config as Config
        if (result.code === ResultEnum.SUCCESS) {
          return result.data
        }
        const msg = opts.errorMsg ?? result.message ?? '请求失败'
        if (!opts.silent) ElMessage.error(msg)
        throw new ApiError(result.code, msg, !!opts.silent)
      },
      (error) => {
        const opts = error.config as Config | undefined
        const msg = opts?.errorMsg ?? error.message ?? '网络请求失败'
        if (!opts?.silent) ElMessage.error(msg)
        return Promise.reject(error)
      },
    )
  }

  get<T>(url: string, options?: RequestOptions): Promise<T> {
    return this.axios.get(url, this.toConfig(options)) as Promise<T>
  }

  post<T>(url: string, data?: unknown, options?: RequestOptions): Promise<T> {
    return this.axios.post(url, data, this.toConfig(options)) as Promise<T>
  }

  put<T>(url: string, data?: unknown, options?: RequestOptions): Promise<T> {
    return this.axios.put(url, data, this.toConfig(options)) as Promise<T>
  }

  delete<T>(url: string, options?: RequestOptions): Promise<T> {
    return this.axios.delete(url, this.toConfig(options)) as Promise<T>
  }

  private toConfig(options?: RequestOptions): Config {
    if (!options) return {}
    const { params, silent, errorMsg, ...rest } = options
    return { params, silent, errorMsg, ...rest }
  }
}

export const ragClient = new HttpClient('/rag')
export const crmClient = new HttpClient('/api')  // 保留 legacy CRM 代理
```

```typescript
// utils/http/index.ts
export { ragClient, crmClient } from './client'
export { ApiError, type ResultData, type RequestOptions } from './types'
```

**设计要点：**

- 不再导出裸 `AxiosInstance`（`ragService` / `crmService`），避免绕过封装
- `post` 的 `data` 支持 `FormData`（上传场景无需额外处理）
- `delete` 的 body 通过 `options.params` 或后续按需扩展 `data` 字段

---

**3. `src/api/` 领域 API 层**

将 URL 字符串与泛型类型集中管理，Store/组件只关心业务语义。

**`api/chat.ts`**

```typescript
import { ragClient } from '@/utils/http'
import type { ChatSessionVO, ChatSessionDTO, Message } from '@/types/AiModule/types'

export const chatApi = {
  getConversations: () =>
    ragClient.get<ChatSessionVO[]>('/ai/history/getConversations'),

  addConversation: (conversationId: string) =>
    ragClient.post<void>('/ai/history/addConversation', null, {
      params: { conversationId },
    }),

  updateConversation: (dto: ChatSessionDTO) =>
    ragClient.post<void>('/ai/history/update', dto),

  deleteConversation: (id: string) =>
    ragClient.delete<void>('/ai/history/deleteById', { params: { id } }),

  getMessages: (conversationId: string) =>
    ragClient.get<Message[]>(`/ai/history/getMessages/${conversationId}`),
}
```

**`api/knowledge.ts`**

```typescript
import { ragClient } from '@/utils/http'
import type { KnowledgeBase, DocumentMeta } from '@/types/knowledgeStore/types'

export interface UploadResult {
  documentId: string
  filename: string
  message: string
  embeddingCount: number
}

export const knowledgeApi = {
  list: () => ragClient.get<KnowledgeBase[]>('/ai/knowledge-base/list'),

  documents: (kbId: string) =>
    ragClient.get<DocumentMeta[]>('/ai/knowledge-base/documents', { params: { kbId } }),

  create: (name: string, description: string) =>
    ragClient.post<void>('/ai/knowledge-base/create', null, {
      params: { name, description },
    }),

  remove: (id: string) =>
    ragClient.post<void>('/ai/knowledge-base/delete', null, { params: { id } }),

  upload: (file: File, kbId: string) => {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('kbId', kbId)
    return ragClient.post<UploadResult>('/ai/file/upload', formData)
  },

  deleteDocument: (id: string) =>
    ragClient.post<void>('/ai/file/delete', { id }),
}
```

**`api/workspace.ts`**

```typescript
import { ragClient } from '@/utils/http'
import type { FileTreeNode, FileInfo } from '@/types/settings/types'

export const workspaceApi = {
  tree: (path = '') =>
    ragClient.get<FileTreeNode[]>('/ai/file/workspace/tree', { params: { path } }),

  list: (path: string) =>
    ragClient.get<FileInfo[]>('/ai/file/workspace/list', { params: { path } }),

  createFolder: (path: string, name: string) =>
    ragClient.post<void>('/ai/file/workspace/folder', null, { params: { path, name } }),

  importFiles: (formData: FormData) =>
    ragClient.post<void>('/ai/file/workspace/import', formData),

  readText: (path: string) =>
    ragClient.get<string>('/ai/file/workspace/read', { params: { path } }),

  readBinary: (path: string) =>
    ragClient.get<{ base64: string; mimeType: string }>(
      '/ai/file/workspace/read/binary',
      { params: { path } },
    ),

  rename: (path: string, newName: string) =>
    ragClient.post<void>('/ai/file/workspace/rename', { path, newName }),

  remove: (path: string) =>
    ragClient.post<void>('/ai/file/workspace/delete', { path }),
}
```

---

**4. 调用方迁移（4 个文件）**

| 文件                                                         | 改动                                                         |
| ------------------------------------------------------------ | ------------------------------------------------------------ |
| [stores/chat.ts](my-chat-vue3/src/stores/chat.ts)            | `chatApi.*` 替换 `request/mutate/ragHttp`；修复 `deleteConversation` 未 await 的 bug |
| [components/ChatBox.vue](my-chat-vue3/src/components/ChatBox.vue) | `chatApi.getMessages`                                        |
| [views/knowledgeStore/KnowledgeStore.vue](my-chat-vue3/src/views/knowledgeStore/KnowledgeStore.vue) | `knowledgeApi.*`                                             |
| [views/settings/components/WorkspaceManagement.vue](my-chat-vue3/src/views/settings/components/WorkspaceManagement.vue) | `workspaceApi.*`                                             |

**迁移示例：`stores/chat.ts`**

```typescript
import { chatApi } from '@/api/chat'

async function fetchChatList() {
  try {
    chatList.value = await chatApi.getConversations()
  } catch { /* 已 toast */ }
}

async function createConversation(id: string) {
  try {
    await chatApi.addConversation(id)
    chatList.value.push({ conversationId: id, title: '' })
    currentChatId.value = id
  } catch { /* 已 toast */ }
}

async function updateConversation(dto: ChatSessionDTO) {
  try {
    await chatApi.updateConversation(dto)
    const index = chatList.value.findIndex(c => c.conversationId === dto.conversationId)
    if (index !== -1 && dto.title !== undefined) {
      chatList.value[index] = { ...chatList.value[index], title: dto.title }
    }
    ElMessage.success('更新成功！')
  } catch { /* 已 toast */ }
}

async function deleteConversation(id: string) {
  try {
    await chatApi.deleteConversation(id)
    const index = chatList.value.findIndex(c => c.conversationId === id)
    if (index !== -1) {
      chatList.value.splice(index, 1)
      currentChatId.value = ''
      ElMessage.success('删除成功！')
    }
  } catch { /* 已 toast */ }
}
```

**try/catch 约定：**

- 拦截器已弹 toast 时，catch 块通常留空或写注释
- 需要自定义错误文案时，在 API 调用传 `{ errorMsg: '...' }`（通过 `ragClient` 的 `RequestOptions`）
- 需要静默失败时传 `{ silent: true }`，在 catch 中自行处理

# 3 Prompt工程和数据支撑

## 3.1 Orchestrate 长对话上下文（滚动摘要 + 近期原文）

主聊天编排**不**给 Worker 挂 `MessageChatMemoryAdvisor(chatId)`（避免与 `orch-*` 临时会话双写）。读路径由 `OrchestrateDialogueContextService` 拼上下文注入 decideNext / Worker / 最终流式：

1. **近期原文窗口**：最近约 10 条 USER/ASSISTANT（单条约 800 字、合计约 4000 字）。
2. **滚动摘要**：旧消息攒够约 6 条时，用无工具 Client 压成中文摘要写入 `chat_session_summary`（按 `covered_until_sequence_id` 增量覆盖）；再与近期原文一起注入。
3. **写路径不变**：回合末仍手动 persist 短 USER + 最终 ASSISTANT；摘要只在读路径惰性更新。

实测：Memory ≤10 条时摘要表可为空（全在窗口内）；超过窗口后会 `INSERT` 并出现非空「会话摘要」段。摘要调用会拖长该轮首包延迟，属有损压缩。

该链路嵌入主线程，没有异步，会影响性能。

## 3.2 工作区

> 专业术语：**AI Workspace Context**（AI 工作区上下文），亦称 **会话级工作目录沙箱**。  
> 对标产品：Cursor / VS Code Copilot 的 Workspace Awareness、Claude Code 的 Project Context。

My-Chat 允许用户为每个对话指定一个服务端磁盘目录作为 **工作区上下文**。进入该对话后，AI 可通过 `FileTools`（一组强类型 `@Tool` 方法）在该目录下执行文件增删改查——类似于在 IDE 中打开一个项目，AI 助手能直接操作该项目的文件。

---

### 3.2.1 业务流程

**完整链路**

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

**三种目录输入方式的对比**

| 方式       | 组件                           | 后端端点                 | 特点                            |
| ---------- | ------------------------------ | ------------------------ | ------------------------------- |
| 手动输入   | `el-autocomplete` 文本框       | —（验证时调用 validate） | 直接输入路径字符串              |
| 目录浏览器 | `DirectoryPicker.vue`          | `/roots` + `/browse`     | 面包屑导航，点击展开子目录      |
| 联想搜索   | `el-autocomplete` + 防抖 300ms | `/suggest`               | 取父目录列表 + 最后一段前缀过滤 |

三者共用 `handleConfirmDirectory` 入口，统一在确认前调用 `validate` 端点做安全校验。

---

### 3.2.2 架构设计

**整体架构**

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

**核心组件详解**

**WorkspaceContext — 线程级工作目录持有者**

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

**WorkspaceUtil — 文件操作引擎**

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

**FileTools — 强类型参数化工具**

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

| 维度           | ShellTool（已废弃）        | FileTools（当前）            |
| -------------- | -------------------------- | ---------------------------- |
| 参数解析       | `split("\\s+")` 字符串拆分 | Spring AI 自动 JSON 反序列化 |
| 空格文件名     | ❌ 被错误拆分               | ✅ 天然支持                   |
| 模型调用准确性 | 需记忆命令格式 `ls <path>` | 只需选工具 + 填参数          |
| `\n` 换行      | ❌ 变成字面量 `\\n`         | ✅ 文本正确写入               |
| 通配符 / 管道  | ❌ 模型频繁误用             | ✅ 无法传入（工具语义明确）   |

**buildWorkspaceSystemPrompt — 动态系统提示**

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

**启动时工作目录校验**

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

**安全校验矩阵**

| 校验点   | 位置                                                | 校验内容                       | 拒绝后的行为                  |
| -------- | --------------------------------------------------- | ------------------------------ | ----------------------------- |
| 启动时   | `WorkspaceUtil.init()`                              | 配置目录存在 + 是目录 + 非禁止 | 回落 fallback 目录            |
| 确认前   | `GET /workspace/validate`                           | 路径存在 + 是目录 + 非禁止     | 弹出 toast 提示，阻止会话创建 |
| 浏览时   | `GET /workspace/browse` → `listAbsoluteDirectory()` | 浏览目标 ± 是禁止目录          | 弹出 toast 提示               |
| 联想时   | `GET /workspace/suggest` → `listSubDirectories()`   | 过滤结果中的禁止目录           | 不返回禁止目录的联想结果      |
| 切换时   | `POST /workspace/switch` → `switchRoot()`           | 目标存在 + 是目录 + 非禁止     | 抛出 SecurityException        |
| 文件操作 | `WorkspaceUtil.resolveSafe()`                       | 解析后路径不逃逸 workRoot      | 抛出 SecurityException        |



---

### 3.2.3 与成熟产品的对比及待改进点

以 **Cursor**（当前 Agentic IDE 的标杆）为参照，分析差距和改进方向。

**已对齐的能力**

| 能力              | My-Chat 实现            | Cursor                  | 状态 |
| ----------------- | ----------------------- | ----------------------- | ---- |
| 会话级工作目录    | ThreadLocal + DB 持久化 | 打开项目即上下文        | ✅    |
| 文件 CRUD 工具    | FileTools (9 methods)   | Agent tools             | ✅    |
| 路径安全校验      | 6 层校验矩阵            | 工作区沙箱              | ✅    |
| 多方式目录选择    | 手动 + 浏览 + 联想      | 文件树 + 最近项目       | ✅    |
| 流式响应 + 思考链 | SSE + [THINKING] 标签   | Streaming with thinking | ✅    |

**关键差距和改进方向**

**差距 1：无代码文件上下文注入（最核心差距）**

**现状**：AI 需要主动调用 `ls` / `cat` 才能了解工作目录中有什么文件。每次对话都需要反复探索目录结构，效率较低。

**Cursor 的做法**：打开项目时自动扫描目录结构，将关键文件（`package.json`、`tsconfig.json`、入口文件等）注入 system prompt 作为初始上下文。AI 无需工具调用即了解项目骨架。

**改进方向**：在 `buildWorkspaceSystemPrompt()` 中增加目录摘要注入。启动对话时自动执行 `tree --max-depth=2`，将前 500 行结果附加到系统提示中。

**差距 2：文件变更感知缺失**

**现状**：AI 通过 `ls` 只能看到文件名和大小，无法判断最近修改了哪些文件、哪些文件是新增的。

**Cursor 的做法**：通过 LSP / Git diff 感知文件变更，AI 能说"你刚才编辑了 `App.vue` 的第 15 行"。

**改进方向**：

- 利用 Java NIO 的 `WatchService` 监听工作目录文件变更事件
- 在系统提示中注入最近变更文件列表（按 `lastModifiedTime` 排序）
- 支持 `git diff` / `git log --oneline` 作为工具（如果工作目录是 Git 仓库）

**差距 3：前端非服务端文件系统**

**现状**：`DirectoryPicker` 浏览的是服务端文件系统，用户选择的是**服务端磁盘上的目录**。这与用户直觉中的"打开本地项目"有偏差——类似 IDEA 的打开项目一般在客户端本地。

**Cursor 的做法**：客户端本地文件系统直接暴露给 Agent，无需通过网络 API 做服务端文件操作。

**改进方向**：

- 短期：在前端增加提示"选择的是服务端目录"，区分于本地目录
- 长期：如果部署模式变为客户端 Agent + 服务端 LLM 代理模式，则可利用 File System Access API 直接操作本地文件

**差距 4：工具调用无进度反馈**

**现状**：AI 调用 `tree` 遍历大目录时，前端无任何进度指示器。大文件 `cat` 被截断后用户不知情。

**Cursor 的做法**：长时间操作显示进度条，被截断的内容有明确提示和"查看更多"入口。

**改进方向**：

- 工具执行超过 2 秒时推送中间状态（WebSocket / SSE 事件）
- 截断内容附带独立查看入口（而非仅文本提示）

**差距 5：工作目录历史与快速切换**

**现状**：每次需重新输入或浏览选择目录。已用过的目录无历史记录。

**改进方向**：

- 记录最近使用的工作目录列表（可存 DB 或 localStorage）
- 打开目录弹窗中展示"最近使用"快捷入口
- 侧边栏展示工作目录路径，支持点击切换

**差距 6：无代码 Lint / 格式化反馈循环**

**现状**：AI 通过 `write` 修改文件后，不会自动运行 lint / 格式化。用户需要人工检查。

**Cursor 的做法**：文件变更后自动触发 LSP 诊断，将 lint 错误反馈给 AI，形成自动修复循环。

**改进方向**：

- 在系统提示中增加指令"文件写入后请检查常见错误"
- 支持 `lint` / `format` 工具方法（如运行 `eslint --fix`）
- 在 Agent 循环中增加"操作后验证"步骤

**差距 7：工作目录与知识库的割裂**

**现状**：工作目录（`work_dir`）和知识库（`kb_id`）是两个独立维度，不能同时激活。

**改进方向**：

- 允许对话同时关联工作目录和知识库
- RAG 检索结果中标注哪些内容来自工作目录文件

---

### 3.2.4 与其他功能模块的关联

| 关联模块       | 关联方式                              | 影响                       |
| -------------- | ------------------------------------- | -------------------------- |
| 聊天对话       | `chat_sessions.work_dir` 存储         | 每个会话独立工作目录       |
| RAG 知识库     | `chat_sessions.kb_id` 独立字段        | 当前互斥，后续可共存       |
| 文件管理       | `FileController` 共享 `WorkspaceUtil` | 目录浏览、文件预览共用引擎 |
| 设置面板       | `WorkspaceManagement.vue` 独立入口    | 全局工作区管理             |
| Agent 工具调用 | `FileTools` 依赖 `WorkspaceContext`   | 确保每请求隔离             |

---

### 3.2.5 关键文件索引

| 文件                                   | 职责                                                         |
| -------------------------------------- | ------------------------------------------------------------ |
| `ChatView.vue`                         | "打开目录"入口 + 目录弹窗 + autocomplete + DirectoryPicker 集成 |
| `ChatList.vue`                         | 侧边栏"打开目录"按钮 + 同上逻辑                              |
| `DirectoryPicker.vue`                  | 服务端目录浏览器弹窗组件                                     |
| `workspace.ts`                         | 前端 API：roots / browse / suggest / switch / validate       |
| `ChatController.java`                  | 流式对话：设置 WorkspaceContext + system prompt              |
| `WorkspaceContext.java`                | ThreadLocal + 跨线程传播                                     |
| `WorkspaceUtil.java`                   | Java NIO 文件操作引擎 + 路径安全检查                         |
| `FileTools.java`                       | 9 个 @Tool 方法，AI 模型调用的文件工具集                     |
| `AiConfiguration.java`                 | ChatClient bean 配置 + defaultSystem + defaultTools          |
| `ContextPropagationConfiguration.java` | Micrometer 自动上下文传播                                    |
| `ChatSessionsService.java`             | 会话 CRUD，work_dir 存取                                     |
| `application.yaml`                     | `app.workspace.root` 配置                                    |
| `schema.sql`                           | `chat_sessions.work_dir` 列定义                              |

## 3.3 文件上传

### 业务流程

```
用户选择文件 → 点击发送
  │
  ├─ 前端（ChatBox.vue）
  │   ├─ useFileUpload: 校验类型/大小/数量 → selectedFiles[]
  │   ├─ useChatStream.sendMessage():
  │   │   ├─ 推文件列表消息（messages.value，USER 类型）
  │   │   ├─ 推用户文字消息（messages.value，USER 类型）
  │   │   ├─ consumeFiles() → 取出文件
  │   │   └─ startStreaming(text, chatId, files)
  │   │         └─ streamChat(options)
  │   │               ├─ FormData: { prompt, chatId, files }
  │   │               └─ POST /rag/ai/normalChat/chat
  │   └─ 服务端流式返回 → 渲染为 ASSISTANT 消息
  │
  └─ 后端（ChatController.multiModalChat）
      ├─ 分离图片 vs 文档
      ├─ buildFileList(docs) → "📎 上传了以下文件：\n- xxx.md（6.4KB）"
      │   └─ 放入 user prompt → 存入 chat_memory
      ├─ extractDocContent(docs) → PDF/TXT 全文
      │   └─ 放入 system prompt → 仅 AI 可见
      ├─ 图片 → 作为 Media 对象附加
      ├─ Flux.error 兜底: 模型不支持图片时返回友好提示
      └─ MessageChatMemoryAdvisor → 写入 spring_ai_chat_memory

刷新页面
  ├─ ChatBox.getMessages(chatId)
  │   ├─ 后端返回包含 user prompt（含文件列表）的历史消息
  │   └─ 检测 "用户的问题：\n" 分隔符 → 拆分为两条 USER 消息
  └─ visibleMessages 过滤 system 消息 → 显示拆分的文件列表 + 用户问题
```

### 涉及代码文件

#### 后端

| 文件                  | 改动                                                         |
| --------------------- | ------------------------------------------------------------ |
| `ChatController.java` | `multiModalChat()` 按文件类型分叉；新增 `buildFileList()` / `extractDocContent()` / `formatFileSize()`；文档全文放入 system prompt，文件列表放入 user prompt |

#### 前端

| 文件                           | 说明                                                         |
| ------------------------------ | ------------------------------------------------------------ |
| `streamChat.ts`                | `ChatStreamOptions.files` 已定义 + FormData 追加（本次未改动，复用已有能力） |
| `ChatBox.vue`                  | 模板：文件选择 input + 悬浮文件面板 + 浮标按钮；script：由 composable 组合 |
| `composables/useFileUpload.ts` | **新增** — 文件选择、校验（类型/大小/数量/去重）、图标、大小格式化 |
| `composables/useChatScroll.ts` | **新增** — 自动滚底、跳转按钮、流式联动                      |
| `composables/useChatStream.ts` | **新增** — 消息发送、流式接收、历史消息拆分、会话切换监听    |

#### 数据库

无需改表。`spring_ai_chat_memory.content` 存储已包含文件列表的 user prompt。

---

### 核心设计决策

**4.1 文档全文放入 system prompt（关键决策）**

**背景**：刷新前后文件消息显示不一致。刷新前是前端构建的整洁文件列表，刷新后是后端返回的文档全文。

**方案**：将文档全文从 user prompt 剥离→放入 system prompt。user prompt 只保留文件列表。

```
user prompt（存入 chat_memory，前端可见）:
  📎 上传了以下文件：
  - foo.pdf（2.3MB）
  
  用户的问题：
  总结这个文件

system prompt（仅 AI 可见，前端过滤不显示）:
  [原有工作目录提示]
  
  用户上传了以下文档内容供参考：
  --- foo.pdf ---
  [全文...]
```

**4.2 消息拆分与过滤**

| 技术                         | 目的                                                       |
| ---------------------------- | ---------------------------------------------------------- |
| `visibleMessages` computed   | 过滤 `messageType === 'system'` 的消息（文档全文）         |
| `getMessages()` 中的拆分逻辑 | 检测 "用户的问题：\n" 分隔符，将后端合并的一条消息拆为两条 |

**4.3 模型不支持图片的兜底**

DeepSeek 不支持多模态（image → Media）。后端 `onErrorResume` 捕获 400 错误，返回友好提示。前端 ElMessage 提示而非崩溃。

---

### 核心设计决策追问

**直接将文本内容拼入提示词是否标准做法？**

**结论：小文件是标准做法，但文件变大后需要 RAG。**

| 做法                             | 方法                           | 适用场景                          | 代表产品                         |
| -------------------------------- | ------------------------------ | --------------------------------- | -------------------------------- |
| **Direct concatenation（当前）** | 全文拼入 system prompt         | 小文件（< 10K tokens ≈ 30页文本） | GPTs、Claude Projects            |
| **Summarize-then-concatenate**   | 先摘要再拼入 prompt            | 中等文件（10K–100K tokens）       | NotebookLM                       |
| **RAG chunk + retrieve**         | 分片 → 向量化 → 检索 TopK 拼入 | 大文件（> 100K tokens）、多文件   | Dify、FastGPT、ChatGPT with Bing |
| **Contextual compression**       | 检索后压缩冗余再插入           | 超长上下文场景                    | LlamaIndex                       |
| **Agentic reading**              | AI 主动调用工具读取文件        | 无上下文硬限制                    | Cursor、opencode、Claude Code    |

**当前 My-Chat 的位置**：Direct concatenation + 50KB 硬截断。对知识库类文档（数 KB 级别）完全够用。对长篇书籍、大文本日志会失效。

**升级路径**：不改当前做法的基础上，增加"当文件超过 N 字符时触发 RAG 流程"的分支，两种策略共存。

### 架构层面的优化方向

**当前架构的问题**

```
用户上传文件 → multiModalChat()
  ├─ 文档提取文本 → 拼入 system prompt（字符串拼接）
  │                    ↑ 整个文件内容一次性写入，无分片、无检索
  ├─ user prompt 拼入文件列表 + 用户问题
  └─ 发给模型 → MessageChatMemoryAdvisor 全文存储
                     ↑ user prompt 持久化到 DB，含文件列表
```

| 问题                    | 后果                                      |
| ----------------------- | ----------------------------------------- |
| 文件内容全部塞入 prompt | 空耗 token，大文件很快撑爆 context window |
| 无分片检索              | 100 个文件无法只检索相关片段              |
| 无缓存                  | 相同文件每次上传都重新提取文本            |
| 消息存储与 prompt 绑定  | 刷新后需手动拆分，架构耦合                |

**目标架构**

```
用户上传文件
  ├─ 小文件（< 10K tokens）→ Direct concatenation（当前做法，不改变）
  └─ 大文件 / 多文件 → RAG 流程（新增分支）
      ├─ DocumentService 分片 → 生成 embedding
      ├─ 存入临时向量空间（会话级 / 文件级隔离）
      ├─ user's prompt 检索相似片段 → 拼入 context
      └─ 文件元信息存入 chat_memory（而非全文）
```

**优化 1：RAG 化文件处理**

| 组件                     | 职责                                                         |
| ------------------------ | ------------------------------------------------------------ |
| `FileSessionVectorStore` | 按 sessionId 隔离的临时向量空间；Spring AI `VectorStore` 接口 + Postgres 实现 |
| `SessionRagAdvisor`      | 接收 `(userPrompt, sessionId)` → 检索相关分片 → 拼入 advisor context |
| `DocumentChunker`        | 复用 `TokenTextSplitter`（已有），上传时同步分片并向量化     |

**优势**：

- 文件数不再受 context window 限制（每个请求只检索 top-5 分片）
- 无需截断（50KB 硬限制解除）
- AI 获得精准的相关片段而非全量噪音

**优化 2：文件内容缓存**

```
上传输入 → DocumentService.parseXxx()
         → 提取文本存入临时文件（或 Redis）
         → 返回 cacheKey

下次同一文件上传 → 命中 cacheKey → 跳过解析
```

**优势**：MD5/ETag 去重，大 PDF 避免反复解析。

**优化 3：消息持久化与 prompt 解耦**

**问题**：现在 user prompt 直接存到 `spring_ai_chat_memory.content`，导致"文件列表 + 用户问题"合为一条，刷新后需手动拆分。

**解耦方案**（最小改动）：

```
user prompt 只存放用户文字 → "总结这个文件"
文件列表作为 metadata 附加到消息
chat_memory.content = "总结这个文件"（干净）
前端通过 metadata 渲染文件列表
```

但此方案需要修改 Spring AI 的 `MessageChatMemoryAdvisor` 消息结构，或者在前端 `getMessages()` 中额外调一次 API 获取消息关联的文件列表（当前拆分逻辑的直接对等替代）。

**长期方案**：引入 `message_attachments` 表。

```sql
CREATE TABLE message_attachments (
    id              VARCHAR(64) PRIMARY KEY,
    conversation_id VARCHAR(255) NOT NULL,
    filename        VARCHAR(500) NOT NULL,
    file_size       BIGINT,
    file_type       VARCHAR(50),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

文件列表从 `spring_ai_chat_memory.content` 中彻底剥离，前后端都通过 `message_attachments` 渲染。

**优化 4：懒加载检索（Cursor 对标）**

**现状**：所有文件内容在发送消息时即提取完成，用户必须先上传才能提问。

**Cursor 的做法**：AI 说"我想看看 `config.json`"，然后主动调用工具 `cat config.json` 读取。用户无需提前上传。

**结合路径**：

- 用户设置工作目录后，AI 可通过 `FileTools` 直接读取工作目录下的文件
- 文件上传独立于文件读取：上传是"我想让你看这个"，FileTools 是"你需要看这个自己去看"

**优先级建议**

| 优先级 | 优化                                        | 投入     | 效果                   |
| ------ | ------------------------------------------- | -------- | ---------------------- |
| P0     | 当前做法 + 50KB 截断（已有）                | —        | 小文件场景已可用       |
| P1     | **RAG 化文件处理**                          | 3-5 天   | 大文件、多文件场景质变 |
| P2     | 文件内容缓存（MD5 去重）                    | 1 天     | 重复文件零开销         |
| P2     | 消息与 prompt 解耦（`message_attachments`） | 2 天     | 前端不需手动拆分       |
| P3     | 懒加载检索 + FileTools 协同                 | 持续迭代 | Cursor 级别体验        |

---

### 与 Cursor / opencode 的差距分析

| 能力            | My-Chat                | Cursor / opencode          | 差距            |
| --------------- | ---------------------- | -------------------------- | --------------- |
| **文件类型**    | image / PDF / txt / md | 任意格式（含二进制）       | 需扩展类型      |
| **上传方式**    | 手动点击 + 文件选择框  | 拖拽 + 粘贴图片 + 文件选择 | 缺少拖拽        |
| **文件预览**    | 纯文本列表             | 行内渲染图片 + PDF 预览    | 无预览          |
| **AI 感知文件** | 后端提取文本供 AI      | AI 直接读本地文件系统      | AI 无法访问本地 |
| **消息一致性**  | 手动拆分后端合并消息   | 天然分离                   | 架构层需优化    |
| **大小限制**    | 10MB                   | 因服务而异                 | 可放宽          |
| **错误提示**    | 后端友好提示           | 前端即时反馈               | 可减少往返      |

---

### 改进展望

**🔴 P0 — 体验修复**

| #    | 改进         | 说明                                        |
| ---- | ------------ | ------------------------------------------- |
| 1    | **拖拽上传** | 支持将文件拖入 input 区域，提升文件选取效率 |
| 2    | **粘贴图片** | 支持 Ctrl+V 粘贴剪贴板图片，自动上传        |

**🟡 P1 — 可用性提升**

| #    | 改进               | 说明                                                        |
| ---- | ------------------ | ----------------------------------------------------------- |
| 3    | **行内预览**       | 图片文件在消息中直接渲染缩略图，点击可放大                  |
| 4    | **文件类型扩展**   | 增加 .docx / .xlsx 等 Office 格式的服务端文本提取           |
| 5    | **大小限制可配置** | 后端可通过 application.yaml 配置 `app.upload.max-file-size` |
| 6    | **批量上传进度**   | 多个大文件时显示上传进度条                                  |

**🟢 P2 — 体验增强**

| #    | 改进                  | 说明                                                         |
| ---- | --------------------- | ------------------------------------------------------------ |
| 7    | **AI 直接读工作目录** | 如果已设置工作目录，AI 可通过 FileTools 直接读取文件，无需上传 |
| 8    | **文件名自动补全**    | 输入框中 @ 触发工作目录文件列表，自动补全路径                |
| 9    | **文件版本管理**      | 同一文件多次上传保留历史版本                                 |
| 10   | **文件关联知识库**    | 上传文件时可选同时加入知识库，一次操作完成对话 + 知识沉淀    |

---

### 数据库依赖

本次功能不涉及数据库变更。消息存储依赖 `spring_ai_chat_memory` 表（Spring AI 框架自动维护）：

```sql
CREATE TABLE IF NOT EXISTS spring_ai_chat_memory (
    id              BIGSERIAL PRIMARY KEY,
    conversation_id VARCHAR(255) NOT NULL,
    type            VARCHAR(50)  NOT NULL,
    content         TEXT         NOT NULL,
    timestamp       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    sequence_id     BIGINT
);
```

`content` 字段存储的 user prompt 中已包含文件列表信息。

# 模型调用模块

## MCP-接入 Smithery 工具箱（Exa Search）

### 原理

Smithery 给你的地址是 **Namespace MCP Endpoint**（见 [Connect to MCPs](https://smithery.ai/docs/use/connect)）：

```text
https://mcp.smithery.run/{namespace}
→ 你的是 https://mcp.smithery.run/2625705206
```

它会把工具箱里所有已连接的 MCP（含 Exa Search）聚合到一个端点。工具名通常带连接前缀（如 `exa_search` / `connectionId.toolName`）。

```mermaid
flowchart LR
  ChatClient["toolChatClient"]
  SyncProvider["SyncMcpToolCallbackProvider"]
  Weather["localhost:8101 weather"]
  Smithery["mcp.smithery.run/2625705206"]
  Exa["Exa Search"]

  ChatClient --> SyncProvider
  SyncProvider --> Weather
  SyncProvider --> Smithery --> Exa
```

鉴权：后端访问该端点需带 **Smithery API Key**（[Account API Keys](https://smithery.ai/account/api-keys)）：

```http
Authorization: Bearer <SMITHERY_API_KEY>
```

Spring AI 2.0 的 `streamable-http.connections` **不支持 YAML 写 headers**，官方做法是注册 `McpClientCustomizer<HttpClientStreamableHttpTransport.Builder>`，在指定连接上调用 `.httpRequestCustomizer(...)`。

URL 切分注意：默认 `endpoint` 是 `/mcp`。Smithery 路径本身就是完整 MCP 路径，应写成：

| 配置项     | 值                         |
| ---------- | -------------------------- |
| `url`      | `https://mcp.smithery.run` |
| `endpoint` | `/2625705206`              |

若写成 `url: https://mcp.smithery.run/2625705206` + 默认 `endpoint: /mcp`，会错误请求 `.../2625705206/mcp`。

### 前置（你本地完成）

1. 在 [smithery.ai/account/api-keys](https://smithery.ai/account/api-keys) 创建 API Key。
2. 确认工具箱里 Exa 已是 **connected**（若状态是 `input_required`/`auth_required`，先在网站完成 Exa API Key / OAuth）。
3. 设置环境变量 `SMITHERY_API_KEY`（不要写进仓库）。
4. IDEA Reload Maven 后重启 `my-chat-server`。

### 需要改/新增的文件

**1. [`my-chat-server/src/main/resources/application.yaml`](my-chat-server/src/main/resources/application.yaml)**

在现有 `spring.ai.mcp.client.streamable-http.connections` 下**追加** Smithery 连接（保留天气服务）：

```yaml
streamable-http:
  connections:
    mcp-weather-server:
      url: http://localhost:8101
      endpoint: /mcp
    smithery-toolbox:          # 连接名，自定义izer 用它过滤
      url: https://mcp.smithery.run
      endpoint: /2625705206    # 你的 namespace，勿再拼 /mcp
```

可适当加大 `request-timeout`（远程搜索常 >30s，例如 `60s`）。

另：

```yaml
app:
  mcp:
    smithery:
      api-key: ${SMITHERY_API_KEY:}
      connection-name: smithery-toolbox
```

**2. 新增 [`my-chat-server/src/main/java/com/mychat/config/SmitheryMcpConfiguration.java`](my-chat-server/src/main/java/com/mychat/config/SmitheryMcpConfiguration.java)**

```java
@Bean
McpClientCustomizer<HttpClientStreamableHttpTransport.Builder> smitheryAuthCustomizer(
    @Value("${app.mcp.smithery.api-key:}") String apiKey,
    @Value("${app.mcp.smithery.connection-name:smithery-toolbox}") String connectionName) {
  return (name, transportBuilder) -> {
    if (!connectionName.equals(name) || apiKey == null || apiKey.isBlank()) {
      return; // 不影响本地天气等无鉴权连接
    }
    transportBuilder.httpRequestCustomizer(
        (req, method, endpoint, body, ctx) ->
            req.header("Authorization", "Bearer " + apiKey));
  };
}
```

关键 import：

- `io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport`
- `org.springframework.ai.mcp.customizer.McpClientCustomizer`

**3. [`AiConfiguration.java`](my-chat-server/src/main/java/com/mychat/config/AiConfiguration.java) / [`McpToolsDiagnostics.java`](my-chat-server/src/main/java/com/mychat/config/McpToolsDiagnostics.java)**

**无需为 Smithery 再改**。`SyncMcpToolCallbackProvider` 已聚合所有 `McpSyncClient`；诊断日志应能看到 Exa 相关工具名。仅可微调 system prompt 一句「可用网页搜索类 MCP 工具时按需调用」。

**4. 前端**

不改。工具经现有 `/ai/normalChat/chat` 链路由模型选择调用。

# 输出后处理

## 可观测性时间线总结与思考回显规划

### 一句话结论

普通聊天（`toolChatClient`）已能以 **NDJSON 事件流**实时展示工具调用时间线，并以本地表 **`chat_assistant_turns`** 按轮持久化 `parts`，刷新后可回放。思考内容目前多为气泡折叠块，**尚未**作为时间线中与工具交错的步骤；旧 `[THINKING]` 标签协议对主路径已过时。

```text
用户 → ChatController(?format=ndjson)
     → ObservabilityStreamAdvisor（旁路 tool_call / tool_result）
     → text_delta / thinking_delta / done
     → 前端 AgentActivityTimeline + Markdown
     → 回合结束异步写入 chat_assistant_turns
     → GET /ai/history/getMessages 合并 parts 回放
```

---

### 三周做了什么

**第 1 周：协议 + 后端旁路**

| 项     | 内容                                                         |
| ------ | ------------------------------------------------------------ |
| 传输   | 同一 POST 上的 **NDJSON**（`application/x-ndjson`）；`format=plain` 保留旧纯文本 |
| 采集   | `ObservabilityStreamAdvisor`（order ≈ ToolCallingAdvisor+100）旁路发事件，**不改** Memory / 工具循环 |
| 事件   | `thinking_delta` / `text_delta` / `tool_call` / `tool_result` / `error` / `done` |
| 关键类 | `ChatStreamEvent`、`ChatStreamEventWriter`、`ChatController` |
| 注意   | Spring AI 2.0 已删除 `streamToolCallResponses`，不可再依赖   |

**第 2 周：前端时间线**

| 项   | 内容                                                         |
| ---- | ------------------------------------------------------------ |
| 消费 | `streamChat` 固定普通聊天 `?format=ndjson`；行缓冲解析       |
| 状态 | `useChatStream` 归约 `streamingParts` / `streamingThinking` / 正文 |
| UI   | `AgentActivityTimeline`（目前主要渲染 **tool**）；`ChatBox` 去掉错误 `THINKING_START` 解析 |
| 范围 | **RAG 仍 plain**，无时间线                                   |

**第 3 周：回放落库**

| 项   | 内容                                                         |
| ---- | ------------------------------------------------------------ |
| 表   | `chat_assistant_turns`（一轮一行：`parts` JSONB + `thinking` + `assistant_ordinal`） |
| 写入 | 流结束异步归约落库；闲聊无轨迹可跳过                         |
| 读取 | `getMessages` → `ChatMessageVO`，按 ordinal（文本兜底）挂 `parts` |
| 约束 | **不改** `spring_ai_chat_memory`；删会话级联清回合表         |
| 踩坑 | PG `jsonb` 不能用默认 `JacksonTypeHandler`（varchar）；需 `PGobject` TypeHandler；`postgresql` 依赖须 compile |

详细设计仍以 `docs/正式迭代前待做/` 下四周文档为准；本文件只做索引级总结。

---

### 当前能力边界（已知未做）

| 未做                     | 说明                                      |
| ------------------------ | ----------------------------------------- |
| RAG `retrieval` 事件     | `RagChatController` 仍 plain + 旧标签痕迹 |
| Agent route 步骤进时间线 | `/ai/agent/route` 未接 ChatBox            |
| 思考与工具交错展示       | 见下文待实现                              |
| Spring AI Session / OTEL | 刻意不做，Memory 旁路表已够 UI 回放       |

---

### 待实现：思考模式回显（多厂商 + 时间线交错）

曾尝试过实现一次，但效果不好，可能放弃这一块。

#### **4.1 动机**

早期 `toThinkingResponse` 用 `[THINKING]…[/THINKING]` 适配 DeepSeek 思考模式；主路径已改 NDJSON 后，该拼接**实质无用**。  
多家模型仍会输出思考，但字段名不一（`reasoningContent` / `thinking` / `reasoning` / `getReasoningContent()`），且当前实现：

1. 只读 `reasoningContent`；
2. **含 `toolCalls` 的帧直接跳过**，工具前思考进不了时间线；
3. 时间线组件过滤掉 `type==='thinking'`，思考只在气泡顶栏折叠；
4. `application.yaml` 中 `thinking.type: disabled`，默认无思考流。

#### **4.2 目标**

1. 退役主聊天 plain 路径的 `[THINKING]` 拼接。  
2. 统一提取多厂商 reasoning → 协议事件 `thinking_delta` / part `type:"thinking"`。  
3. 时间线顺序：`thinking → tool → thinking → tool → … → 正文`。  
4. 历史回放：`parts` 内 thinking 片段保持交错顺序（**不必改 schema**）。

#### **4.3 设计要点**

**提取层**（建议新建 `ReasoningContentExtractor`）：

| 优先级 | 来源                                                        |
| ------ | ----------------------------------------------------------- |
| 1      | metadata `reasoningContent`                                 |
| 2      | metadata `thinking`（Ollama 等）                            |
| 3      | metadata `reasoning`（部分兼容网关）                        |
| 4      | 反射 `getReasoningContent()`（DeepSeek 专用类型，无则跳过） |

- 增量发送（累计全文做 suffix diff，或识别纯 delta），避免重复刷屏。  
- **工具帧也提取 thinking**；该帧仍不发 `text_delta`。  
- 不做 Anthropic signed / redacted 块回传 API（属框架职责）。

**归约 / 前端**：

- `TurnPartsReducer`：thinking 与 tool 交错写入 `parts`；`thinking` 列仍存全文兜底。  
- `useChatStream`：同步维护时间线中的 thinking part。  
- `AgentActivityTimeline`：渲染 thinking（折叠/次要样式）。  
- `ChatBox`：时间线已有 thinking 时隐藏顶栏重复折叠；旧数据无 parts 时保留顶栏。

**配置**：默认可继续 `disabled`；验收时临时 `thinking.type: enabled`。  
开启后若多轮工具出现 400，优先查 Spring AI 是否回传 `reasoning_content`，**不用自造标签绕过**。

---

### 相关代码索引（便于后人跳转）

| 区域      | 路径                                                         |
| --------- | ------------------------------------------------------------ |
| 事件 DTO  | `my-chat-server/.../common/ChatStreamEvent.java`             |
| Advisor   | `my-chat-server/.../utils/advisor/ObservabilityStreamAdvisor.java` |
| 流式入口  | `my-chat-server/.../controller/ChatController.java`          |
| 归约落库  | `my-chat-server/.../chat/stream/TurnPartsReducer.java`、`ChatAssistantTurnServiceImpl` |
| 历史合并  | `my-chat-server/.../controller/ChatHistoryController.java`   |
| 前端流    | `my-chat-vue3/src/utils/streamChat.ts`、`composables/useChatStream.ts` |
| 时间线 UI | `my-chat-vue3/src/components/AgentActivityTimeline.vue`、`ChatBox.vue` |
| DDL       | `my-chat-server/src/main/resources/schema.sql`（`chat_assistant_turns`） |

# 质量监测系统

## 召回测试

对齐 Dify「检索测试」、FastGPT「搜索测试」：给**知识库配置者**看命中片段和分数，不进聊天、不调 LLM。本项目已有按 `kbId` 过滤的 `SearchRequest` 与知识库 `topK` / `similarityThreshold`，本轮只把同一检索摊开返回。

### 边界（贴市场、不膨胀）

- **做**：选中知识库 → 输入模拟问题 → 列出片段正文、分数、来源文件名；可用该库已保存的 topK/阈值，或在测试页临时改这两项（不写库，对应 Dify「仅当前测试生效」）。
- **不做**：检索历史表、混合检索/Rerank、父子分段、聊天引用角标、新路由、改 `[schema.sql](my-chat-server/src/main/resources/schema.sql)`（无新表无新列）。

### 流程

```mermaid
sequenceDiagram
  participant UI as KnowledgeStore
  participant API as KnowledgeBaseController
  participant Svc as KnowledgeRetrievalService
  participant VS as VectorStore

  UI->>API: POST documents/retrieve-test
  API->>Svc: query plus optional topK threshold
  Svc->>Svc: 读知识库；缺省用库内 topK 阈值
  Svc->>VS: similaritySearch filter kbId
  VS-->>Svc: Document list with score
  Svc-->>UI: hits filename score text
```



与问答共用过滤表达式 `kbId == '{id}'`（入库 metadata 已是 `kbId` / `filename` / `documentId`，见 `[DocumentService](my-chat-server/src/main/java/com/mychat/service/knowledge/DocumentService.java)`）。

### 后端

**1. 抽出检索请求构造（避免测试与 Advisor 滤条件分叉）**

新增小工具类 `[KbSearchRequests](my-chat-server/src/main/java/com/mychat/service/knowledge/KbSearchRequests.java)`（或放在 `knowledge` 包下同名）：`filtered(kbId, topK, threshold)` 返回已设 `topK`、`similarityThreshold`、`filterExpression` 的 `SearchRequest.Builder`。

`[AgentRoutingService.buildKbAdvisor](my-chat-server/src/main/java/com/mychat/service/agent/AgentRoutingService.java)` 改为用该 Builder `.build()`，行为与现网一致（Advisor 仍自己填 query）。

**2. `KnowledgeRetrievalService`**

新类，职责仅「召回测试」：

- 知识库必须存在
- `query` 去空白后非空，长度上限 1000
- `topK` / `threshold` 未传则用该库已存值（`KnowledgeBaseSettings.topKOrDefault` / `thresholdOrDefault`）；传入则只做与设置页相同的范围校验（1–20、0–1），**不 update 知识库**
- `vectorStore.similaritySearch(builder.query(query).build())`
- 映射为 DTO：`text`、`score`（`Document.getScore()`，没有则 metadata）、`filename`、`documentId`；保持 VectorStore 返回顺序（分数高在前）
- 空列表合法（阈值滤光），不要当错误

**3. API / DTO**

- `POST /ai/knowledge-base/documents/retrieve-test`
- Body：`kbId`、`query`，可选 `topK`、`similarityThreshold`
- 响应：`kbId`、实际使用的 `topK` / `similarityThreshold`、`hits[]`
- 挂在 `[KnowledgeBaseController](my-chat-server/src/main/java/com/mychat/controller/KnowledgeBaseController.java)`，`IllegalArgumentException` → 400，风格同 update / reindex

类与 public 方法一行注释；检索方法内按「解析参数 / 检索 / 映射 hits」分步注释。

### 前端

放在现有 `[KnowledgeStore.vue](my-chat-vue3/src/views/knowledgeStore/KnowledgeStore.vue)`，不新开路由（Dify 也是知识库内一页，不是聊天里）。

- 顶栏增加「召回测试」，与「设置 / 上传」并列；未选库时禁用
- `content-area` 在「文档表」和「召回面板」间切换（切库时回到文档表并清空结果）
- 面板：上方 `el-input` textarea +「测试」；旁侧两个 `el-input-number`（topK、阈值），打开面板时从 `currentKb` 拷贝；文案注明**仅本次有效，不保存到设置**
- 结果：`el-empty` 无命中；有则卡片/列表，每条显示分数（`el-tag`）、文件名、正文（过长折叠）
- `[knowledge.ts](my-chat-vue3/src/api/knowledge.ts)` + `[types.ts](my-chat-vue3/src/types/knowledgeStore/types.ts)` 增加 `retrieveTest`

不改聊天页、不改 `[notify.ts](my-chat-vue3/src/stores/notify.ts)`。

# @ 其他

## @.1 异步任务 + 消息通知

### @.1.1 整体设计

总体是发布者-订阅者模型。业务线负责把事做完并记到库里；记录线（JobEventHub）负责在「做完」的那一刻，找到还挂着的浏览器把消息送出去。 两条线靠hub的 `emit` 联系。

一些细节可能会小规模用到特定设计模式，比如策略 + 注册表（Strategy + Registry）： `JobHandler` 是策略接口；`type()` 是策略名；Dispatcher 里的 `Map<String, JobHandler>` 是注册表。`handlers.get(jobType).execute(job)` 按类型选实现，Dispatcher 不写死 `KbIngestJobHandler`。

异步任务机制不是一条直线互相调用，而是**两条线汇合**：一条写任务并在后台跑，一条给浏览器订阅状态。`JobController` **不提交任务**，只负责查询和挂 SSE。

| 类                    | 角色                                      | 谁调用它                                                     |
| --------------------- | ----------------------------------------- | ------------------------------------------------------------ |
| `AsyncJobServiceImpl` | 前台窗口：落库 + 踢一脚调度器             | 业务（如 `DocumentIngestService`）、`JobController.active()` |
| `AsyncJobDispatcher`  | 后台工人：改状态、找 Handler、跑完再改库  | 只有 Service 的 `submit()`                                   |
| `JobEventHub`         | 广播站：记住所有浏览器长连接，转发状态    | Dispatcher 的 `emit`；Controller 的 `subscribe`              |
| `JobController`       | HTTP 门面：`/active` 补洞、`/events` 订阅 | 前端 `notifyStore`                                           |

页面一打开，前端就：

1. `GET /ai/jobs/events` → `JobController.events()` → `jobEventHub.subscribe()`  
   Hub 把这条 `SseEmitter` 放进名单，立刻写一行 `connected`。
2. `GET /ai/jobs/active` → `JobController.active()` → `asyncJobService.listActive()`  
   把刷新前还在 `PENDING`/`RUNNING` 的任务补回来（SSE 不重放历史）。

这时 Hub 里已经有连接，但还没有任何任务。

```
浏览器 ──GET /events──► JobController ──subscribe()──► JobEventHub（名单 +1）
浏览器 ──GET /active──► JobController ──listActive()──► AsyncJobServiceImpl
```

具体业务不经过 `JobController`，直接调 Service：

```
DocumentIngestService
    └── asyncJobService.submit("kb_ingest", title, docId, payload)
```

**1. 提交：`AsyncJobServiceImpl`**

```47:64:my-chat-server/src/main/java/com/mychat/job/AsyncJobServiceImpl.java
    public AsyncJobVO submit(...) {
        // insert：PENDING
        asyncJobMapper.insert(job);
        asyncJobDispatcher.dispatch(job.getId());  // 异步，立刻返回
        return ...selectById...;                   // 多半还是 PENDING
    }
```

HTTP 上传线程在这里结束。`dispatch()` 上有 `@Async(jobExecutor)`，所以 **不会等向量化**。

**2. 执行：`AsyncJobDispatcher`（`job-1` / `job-2` 线程）**

```
PENDING → markRunning → RUNNING → emit
       → handlers.get(jobType) → KbIngestJobHandler.execute
       → SUCCEEDED / FAILED → emit
```

两次推送都走同一个 Hub：

```70:70:my-chat-server/src/main/java/com/mychat/job/AsyncJobDispatcher.java
        jobEventHub.emit(AsyncJobMapperSupport.toVo(job));
```

```119:119:my-chat-server/src/main/java/com/mychat/job/AsyncJobDispatcher.java
        jobEventHub.emit(AsyncJobMapperSupport.toVo(latest));
```

Dispatcher **不认识** Controller，也不认识浏览器。它只认识 Hub。

**3. 广播：`JobEventHub`**

`emit` 遍历名单，给每条连接发事件名为 `job` 的 JSON（`AsyncJobVO`）。用户已经切到聊天页也能收到，因为连接是全局的，不绑知识库页。

```
AsyncJobDispatcher.emit()
        │
        ▼
JobEventHub.emitters[] ──SSE event:job──► 标签页 A
                       ──SSE event:job──► 标签页 B
```

**4. 前端消费（不在这四个类里，但是闭环）**

`notifyStore` 听 `job` 事件：`RUNNING` 记进进行中列表；`SUCCEEDED`/`FAILED` 弹窗 + 音效。知识库页再订阅终态去刷新文档列表。

整体图示：

```
                    ┌─ 页面加载 ─────────────────────────────────────────┐
                    │  JobController                                     │
                    │    /events  → JobEventHub.subscribe()  （挂长连接） │
                    │    /active  → AsyncJobService.listActive()（补洞）  │
                    └────────────────────────────────────────────────────┘

业务 submit（上传文档等）
        │
        ▼
AsyncJobServiceImpl          ← 只写库、踢调度，立刻返回 VO
        │ insert PENDING
        │ dispatch(jobId)     ← @Async，换到 job 线程
        ▼
AsyncJobDispatcher
        │ RUNNING + emit ──────────────────► JobEventHub ──SSE──► 浏览器
        │ handler.execute()
        │ SUCCEEDED/FAILED + emit ─────────► JobEventHub ──SSE──► 浏览器
        ▼
     结束（库里有终态；Hub 不存历史）
```

### @.1.2 为何这样设计

状态更新及时：业务执行时会充当发布者推送状态，订阅者通过长连接监听随时接收新状态通知。

本业务是单向消息通知，需要半双工通信通道。前端通过`RESTFUL API`开启一个异步任务之后，需要监听后端的通知，等待后端把任务做完后发一个完成通知，并且只有服务端向客户端发消息，暂时没有客户端向服务端发消息。用SSE模型更合适。传统Result实体类封装响应是发了请求马上回应并关闭连接，但后端执行任务的时间长短不固定，发得太早任务还没结束，发得太晚超时了误以为请求失败。不用Websocket是因为它是全双工通信，对于目前的项目太重，用不上。

| 做法                      | 结论                                                 |
| ------------------------- | ---------------------------------------------------- |
| 纯前端队列                | 刷新/关页丢失，失败无法落 `FAILED`                   |
| Redis / RabbitMQ / Celery | 单机过重                                             |
| 聊天同款 NDJSON `Flux`    | 浏览器 `EventSource` 吃不了，且绑在某次聊天请求上    |
| 标准 SSE + 任务表         | 与 Element Plus 通知中心接法一致；离开业务页连接仍在 |

和本仓库的契合点：

- PostgreSQL + MyBatis-Plus，风格对齐 `knowledge_base`
- Tomcat 已开虚拟线程；**后台任务仍用有界池**，避免以后 embedding 打满 API
- `ragClient` 会解包 `Result<T>` 且 timeout=25s，**不能**订 SSE

---

### @.1.3 数据模型

表 [`async_job`](../../my-chat-server/src/main/resources/schema.sql)：

| 列              | 含义                                            |
| --------------- | ----------------------------------------------- |
| `id`            | UUID                                            |
| `job_type`      | Handler 注册名，如后续 `kb_ingest`              |
| `status`        | `PENDING` → `RUNNING` → `SUCCEEDED` \| `FAILED` |
| `title`         | 通知标题（用户可见）                            |
| `ref_id`        | 业务主键，如 `document_meta.id`                 |
| `payload`       | JSON 字符串，Handler 入参                       |
| `error_message` | 失败原因（调度器截断到 1000 字）                |
| `finished_at`   | 进入终态时间                                    |

终态不再改。无 `user_id`（当前单机单用户）。

---

### @.1.4 后端

**关键类**

| 类                                                           | 职责                                                         |
| ------------------------------------------------------------ | ------------------------------------------------------------ |
| [`AsyncJobService.submit`](../../my-chat-server/src/main/java/com/mychat/service/AsyncJobService.java) | insert `PENDING`，立刻 `dispatch`，HTTP 不等待               |
| [`AsyncJobDispatcher`](../../my-chat-server/src/main/java/com/mychat/job/AsyncJobDispatcher.java) | 独立 Bean 上 `@Async("jobExecutor")`，避免 Service 自调用导致异步失效 |
| [`JobHandler`](../../my-chat-server/src/main/java/com/mychat/job/JobHandler.java) | `type()` + `execute(job)`；成功返回，失败抛异常              |
| [`JobEventHub`](../../my-chat-server/src/main/java/com/mychat/job/JobEventHub.java) | 进程内 `SseEmitter` 扇出；15s 注释心跳                       |
| [`StaleJobRecovery`](../../my-chat-server/src/main/java/com/mychat/job/StaleJobRecovery.java) | 启动时把卡住超过 `app.jobs.stale-minutes`（默认 10）的 PENDING/RUNNING 标 FAILED |
| [`AsyncConfiguration`](../../my-chat-server/src/main/java/com/mychat/config/AsyncConfiguration.java) | `@EnableAsync` + `@EnableScheduling`；池 core=2 / max=4 / queue=100 / CallerRuns |

调度步骤（`dispatch`）：

1. CAS：`PENDING → RUNNING`（`update=0` 则跳过，防重复）
2. SSE 推 `RUNNING`
3. 按 `job_type` 找 Handler；没有则 `FAILED`
4. `execute`；异常 → `FAILED` + `error_message`
5. SSE 推终态

**HTTP**

[`JobController`](../../my-chat-server/src/main/java/com/mychat/controller/JobController.java) `/ai/jobs`：

| 方法 | 路径      | 说明                                                         |
| ---- | --------- | ------------------------------------------------------------ |
| GET  | `/active` | `Result<List<AsyncJobVO>>`，刷新后补未完成任务               |
| GET  | `/events` | **SSE**，`event: job`，data 为 VO JSON；**不是** `Result` 信封 |

验收接口 `POST /ai/jobs/demo` 与 `DemoSleepJobHandler` **已删除**。

**长连接超时（必配）**

否则 SSE 几十秒被掐断：

- [`application.yaml`](../../my-chat-server/src/main/resources/application.yaml)：`spring.mvc.async.request-timeout: 24h`
- [`vite.config.ts`](../../my-chat-vue3/vite.config.ts) `/rag` 代理：`timeout` / `proxyTimeout` = 24h

`JobEventHub` 另设 Emitter 24h + `Cache-Control: no-cache`、`X-Accel-Buffering: no`。

---

### @.1.5 前端

| 文件                                                         | 职责                                                         |
| ------------------------------------------------------------ | ------------------------------------------------------------ |
| [`stores/notify.ts`](../../my-chat-vue3/src/stores/notify.ts) | 先 `listActive`，再 `EventSource('/rag/ai/jobs/events')`；仅终态弹窗 |
| [`App.vue`](../../my-chat-vue3/src/App.vue)                  | `onMounted(connect)`，路由切换不断线                         |
| [`api/jobs.ts`](../../my-chat-vue3/src/api/jobs.ts)          | REST 用 `ragClient`；流用原生 `EventSource`                  |
| [`utils/notifySound.ts`](../../my-chat-vue3/src/utils/notifySound.ts) | 双音门铃连响三遍（约 2s）                                    |

行为约定：

- `RUNNING` 只更新 `activeJobs`，不弹窗
- `SUCCEEDED` / `FAILED`：`ElNotification`（5s）+ 音效；从 `activeJobs` 移除
- 音效需用户手势：首次 `pointerdown` 解锁 `AudioContext`；业务提交前也可调 `notifyStore.unlockSound()`
- 静音策略下 **弹窗仍出现**
- `onJobTerminal(cb)`：给知识库页等挂「刷新列表」；本阶段无调用方

不要用 `ElMessageBox`（会打断当前页）。不要用系统 `Notification` 权限弹窗。

---

### @.1.6 如何接入新业务（知识库入库模板）

1. 实现 `JobHandler`，`type()` 返回稳定字符串（如 `kb_ingest`）。
2. 业务 API **同步只做**校验 + 落盘/插元数据 + `submit(...)`，立即返回。
3. Handler 从 `payload` / `refId` 读入参；成功正常 return，失败抛异常。
4. 需要刷新某页时：该页 `onMounted` 里 `notifyStore.onJobTerminal(...)`，按 `jobType` / `refId` 拉数据。
5. 用户可听见声音：在「提交」点击里调用 `unlockSound()`。

```java
@Component
public class KbIngestJobHandler implements JobHandler {
    @Override
    public String type() {
        return "kb_ingest";
    }

    @Override
    public void execute(AsyncJob job) throws Exception {
        // 解析 payload / refId → 切段 → VectorStore.add
    }
}
```

```java
asyncJobService.submit("kb_ingest", "向量化：" + filename, documentId, payloadJson);
```

---

### @.1.7 明确不做 / 已知缺口

**不做（本阶段）**

- Redis、MQ、WebSocket、任务中心完整 UI（取消、进度条）
- 多用户鉴权、任务权限
- 浏览器系统通知

**缺口（后续可补）**

- SSE 断线期间完成的任务：重连不重放历史；`/active` 只含未完成，可能漏弹窗
- 单进程 `JobEventHub`：多实例部署互不可见（要上 Redis pub/sub 或 MQ）
- 无进度字段：只有四态，没有 0–100%
- 无取消 API

---

### @.1.8 相关代码索引

后端：`entity/po/AsyncJob.java`、`mapper/AsyncJobMapper.java`、`service/AsyncJobService.java`、`job/*`、`controller/JobController.java`、`config/AsyncConfiguration.java`

前端：`types/jobs.ts`、`api/jobs.ts`、`stores/notify.ts`、`utils/notifySound.ts`、`App.vue`