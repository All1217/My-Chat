项目构建了一个 agent 系统，并在此基础上着重研究如何搭建适用于复杂生产环境的 RAG 系统。此外，也会扩展一些自用的功能模块。2026-11 的底座已迁移到**通用 Agent Runtime**（场景 Profile + 持久化 Run），主聊天、股市分析、模拟炒股共用同一套编排；自用模块中模拟炒股已扩展出账本、研究卡片、AI 操盘与五条定时工作流。

# 1 agent业务总览

从用户进入聊天界面输入提示词按下回车开始，系统内部大概会经历以下流程：构造上下文、调用模型完成工作、整理最终 AI 回答、由质量监测系统评估该回答是否符合用户需求、向用户展示答案。

```mermaid
flowchart LR
  UI["用户交互<br/>Vue 3 / fetch NDJSON"] --> Entry["ChatController<br/>绑工作区 / kbId / 附件"]
  Entry --> Pipeline["ChatTurnPipeline<br/>emit_route → load_dialogue → assemble_context<br/>→ orchestrate → stream_final → quality_loop"]
  Pipeline --> Runtime["Agent Runtime<br/>Profile 白名单 + 预算 + 决策循环"]
  Runtime --> Actions["retrieve_kb / file / search / shell / general"]
  Actions --> Model["模型调用<br/>ChatClient / MCP / 搜索"]
  Model --> Runtime
  Runtime --> Final["ChatTurnFinalizer<br/>Memory / chat_assistant_turns"]
  Final --> UI
  Pipeline --> Quality["质量环<br/>Evaluator-Optimizer"]
```

每一个流程背后都会牵涉到大量代码，这些代码在逻辑层面可划分为几个模块，比如用户在聊天界面输入提示词命令 AI 工作，这个涉及到**用户交互模块**；构造上下文涉及到 **Prompt 工程**和**数据支撑模块**；具体让 AI 执行工作需要**模型调用模块**，这个模块是比较核心的模块；AI 生成回答后，不能直接给用户看，还要把它修改成同时符合用户需求和系统内部规范的形式，这需要用到**输出后处理模块**；此外，AI 的回答还需要经过**质量监测模块**审核，以防回答偏离用户需求或是违反法律法规。

## 1.1 当前模块地图（2026-11）

| 模块 | 当前落点 | 说明 |
| --- | --- | --- |
| 用户交互 | `my-chat-vue3/src/views/`、`apps/` | 大厅可挂应用；主聊天用 fetch 读 NDJSON |
| Prompt / 数据支撑 | `agent/context/`、`service/agent/context/`、`service/knowledge/` | ContextBundle、滚动摘要、工作区、RAG |
| 模型调用 | `config/ChatClientAssembler`、`mcp/`、`tools/` | ChatClient、工具白名单、MCP、联网搜索 |
| 输出后处理 | `service/agent/pipeline/`、`utils/` | 事件归约、时间线落库、附件拆分 |
| 质量监测 | `agent/quality/`、`service/agent/quality/`、`service/agent/pipeline/stage/QualityLoopStage` | 写盘质量环、召回测试 |
| 通用 Agent Runtime | `agent/runtime/`、`agent/persistence/` | 场景 Profile、Run/Step/Approval、指标 |
| 自用功能包 | `apps/market/`、`apps/papertrade/`、`apps/roleplay/` | 股市分析、模拟炒股、角色扮演 |

**读代码入口**：`ChatController` → `ChatOrchestrateStreamService` → `ChatTurnPipeline` → `AgentRuntime`。加长主链路 = 新增 `ChatTurnStage` 并在 `ChatTurnPipeline` 构造器列表插一行，不要把 Memory / done 做成普通 Stage。



# 2 用户交互

项目采用C/S架构，前后端分离。其中前端为了实现流式聊天响应，不用Axios封装的api请求后端接口，而是用fetch：[`streamChat.ts`](../my-chat-vue3/src/utils/streamChat.ts)。

一般和AI聊天采用SSE流式输出，这类业务场景里AI吐出的内容可能很长，把一整段思考内容打印出来，再加上最终的回答，整个过程消耗几十分钟也说不定。所以前端和后端之间需要长连接，让服务器（AI）持续且单向地朝客户端推送信息流。

Axios更适合一次性请求-响应模式，并不原生支持 Server-Sent Events（SSE）这种长连接、流式数据推送协议。axios 基于 `XMLHttpRequest`，而 XHR 没有标准的 `ReadableStream`，虽然可以通过 `onDownloadProgress` 拿到部分 `responseText`，但它是累积文本，不是真正的流，你要自己记录上次读取位置、截取新增内容、处理分块边界、处理 UTF-8 多字节字符被截断、防止响应越长内存越大……总之拔高了开发成本。

而且 axios 的默认 `transformResponse`、拦截器、JSON 解析逻辑，会干扰 `text/event-stream` 这种非一次性 JSON 响应。虽然可以配置 `responseType: 'stream'`开启流式输出，并关闭拦截响应和超时判断，但相当于绕远路，浪费开发成本。

fetch 原生暴露 ReadableStream，适合逐 token 读取。而且 AI 聊天经常需要“停止生成”，`fetch` 可以配合 `AbortController`实现中断流式输出。fetch 还支持 POST、自定义 header、cookie、跨域配置等，比 axios 更灵活、更支持底层自定义。因此在AI聊天场景中使用fetch比axios更常见。

Websocket协议也可实现SSE，但是Websocket是全双工，常规AI聊天仅用半双工（服务器推送到客户端）就够用，Websocket太“重”。

## 2.1 SSE通信

> 口径说明：本项目**聊天主链路实际使用 NDJSON**（`application/x-ndjson`，一次 POST 上逐行返回），SSE 只用于异步任务通知（`/ai/jobs/events`）。下文讲 Reactor 流式模型与 SSE 概念，两者在「服务端持续推送、前端边收边画」上是相通的。

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

## 2.2 RESTful API 分层（已完成）

> 状态（2026-11）：已完成。`utils/http/{types,client,index}.ts` 与 `api/{chat,knowledge,workspace,jobs,model}.ts` 已落地；旧 `utils/http.ts`、`utils/request.ts` 已删除；`stores/*` 与页面不再直接持有 Axios 实例。下方保留重构前的动机与设计，作为决策留档。

**重构前的问题（留档）**

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
- 错误处理分散在旧 `http.ts` 拦截器与旧 `request.ts` 两处，且部分调用绕过 `request`
- `request` / `mutate` / `requestAndSet` 三个包装器语义重叠；`requestAndSet` 未被使用
- `crmHttp` 已导出但全项目无引用

**落地架构**

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

**当前文件结构（已落地）**

```
my-chat-vue3/src/
  utils/http/
    types.ts      # ApiError、RequestOptions
    client.ts     # HttpClient 类 + ragClient / crmClient
    index.ts      # barrel export
  api/
    chat.ts       # 会话 / 历史消息
    knowledge.ts  # 知识库 + 文档上传 + 召回测试
    workspace.ts  # 工作区文件管理
    jobs.ts       # 异步任务查询（SSE 用原生 EventSource）
    model.ts      # 大模型管理
```

已删除旧文件：

- `my-chat-vue3/src/utils/http.ts`
- `my-chat-vue3/src/utils/request.ts`

保留：[streamChat.ts](../my-chat-vue3/src/utils/streamChat.ts)（流式仍用 `fetch` + NDJSON，不走 Axios）。

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

**4. 调用方迁移（已完成）**

| 文件 | 改动 |
| --- | --- |
| `stores/chat.ts` | `chatApi.*` 替换 `request/mutate/ragHttp`；修复 `deleteConversation` 未 await 的 bug |
| `components/ChatBox.vue` | `chatApi.getMessages` |
| `views/knowledgeStore/KnowledgeStore.vue` | `knowledgeApi.*`（含召回测试、分段、重新向量化） |
| `views/settings/components/WorkspaceManagement.vue` | `workspaceApi.*` |
| `views/settings/components/ModelManagement.vue` | `modelApi.*` |
| `stores/notify.ts` / `App.vue` | `jobsApi` 查询未完成任务；SSE 仍用原生 `EventSource` |

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

该链路在回合内串行执行（`LoadDialogueContextStage` 放到 boundedElastic），摘要更新会拉长该轮首包延迟；目前没有异步预热或缓存。

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
                                                             ├─ resolveKbId(chatId, kbId)
                                                             └─ ChatTurnPipeline
                                                                  ├─ AssembleContextStage：WorkspacePromptBuilder
                                                                  │    + 浅层摘要 + 最近修改文件 + RAG 目录
                                                                  └─ OrchestrateStage：Agent Runtime
                                                                       (MAIN_CHAT Profile，file / retrieve_kb / shell …)
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
│Context  │ │(@Tool x10)│ │(Java NIO)    │
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

不同于旧版 `ShellTool` 把文件操作塞进单一 `executeCommand(String)` + `split("\\s+")` 字符串解析，`FileTools` 将每个操作拆分为独立 `@Tool` 方法（当前 10 个：ls / tree / cat / grep / stat / write / mkdir / rm / mv / cp）：

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

| 维度           | 旧版 ShellTool（文件单入口，已废弃） | FileTools（当前）            |
| -------------- | ------------------------------------ | ---------------------------- |
| 参数解析       | `split("\\s+")` 字符串拆分           | Spring AI 自动 JSON 反序列化 |
| 空格文件名     | ❌ 被错误拆分                         | ✅ 天然支持                   |
| 模型调用准确性 | 需记忆命令格式 `ls <path>`           | 只需选工具 + 填参数          |
| `\n` 换行      | ❌ 变成字面量 `\\n`                   | ✅ 文本正确写入               |
| 通配符 / 管道  | ❌ 模型频繁误用                       | ✅ 无法传入（工具语义明确）   |

> 2026-11 起 `ShellTool` 以新定位回归：只负责构建、git、脚本等命令（60 秒超时、输出截断、关机 / 磁盘类危险命令拦截），不再承担文件读写；文件操作仍走 `FileTools`。

**WorkspacePromptBuilder — 动态系统提示 + 浅层摘要**

主链路不再在 `ChatController` 里手写提示，而是由 `utils/WorkspacePromptBuilder` 生成：路径规则 + 工作区浅层摘要（depth≤2、最多 80 行、跳过 `node_modules/.git/target` 等）+ 最近修改文件（最多 8 个）。

```java
sb.append(String.format("""
        所有涉及文件的查看、创建、写入、修改、删除、重命名、复制操作，务必积极调用可用工具实际执行。
        不能在回复中假装执行了文件操作。
        当前工作目录: %1$s
        路径规则：所有路径都是相对于当前工作目录的**相对路径**。
        不要把工作目录名 "%2$s" 作为路径前缀。
        ✅ 正确: path="src/components/App.vue"
        ✅ 正确: path="README.md"
        ❌ 错误: path="%2$s/src/components/App.vue"
        ❌ 错误: path="%2$s/README.md"
        上下文策略：若下方已提供「工作区浅层摘要」，询问顶层目录、项目骨架、有哪些文件夹时，
        **优先直接根据摘要回答**，不必先调用 ls/tree；仅当需要完整树、文件内容或写改时再调工具。
        """, rootPath, name));
```

系统提示中明确告知模型：（1）当前工作目录路径；（2）必须使用**相对路径**；（3）不得把目录名当作路径前缀——这是从实际调试中总结出的常见 AI 幻觉模式。摘要段让模型不必每轮先 `ls/tree`；扫描失败只省略摘要，不影响对话。

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
| 文件 CRUD 工具    | FileTools (10 methods)  | Agent tools             | ✅    |
| 浅层骨架注入      | WorkspacePromptBuilder（depth≤2 + 最近修改） | 项目概要注入 | ✅    |
| 路径安全校验      | 6 层校验矩阵            | 工作区沙箱              | ✅    |
| 多方式目录选择    | 手动 + 浏览 + 联想      | 文件树 + 最近项目       | ✅    |
| 流式响应 + 思考链 | NDJSON 时间线           | Streaming with thinking | ⚠️ 思考未接线 |

**关键差距和改进方向**

**差距 1：代码文件上下文注入（已部分解决）**

**原现状**：AI 需要主动调用 `ls` / `cat` 才能了解工作目录中有什么文件，每轮反复探索。

**2026-11 已做**：`WorkspacePromptBuilder` 在系统提示中注入「工作区浅层摘要」（depth≤2、最多 80 行、跳过依赖/构建目录）和「最近修改文件」（最多 8 个），模型可直接回答顶层结构，减少无效 `ls/tree`。

**仍差**：关键文件内容（`package.json`、入口文件等）尚未自动读取注入；摘要上限 80 行，大仓库仍需工具补足。

**差距 2：文件变更感知（已部分解决）**

**原现状**：AI 通过 `ls` 只能看到文件名和大小，无法判断最近修改了哪些文件。

**2026-11 已做**：`WorkspacePromptBuilder` 按 `lastModifiedTime` 注入最近修改文件列表；`ShellTool` 已可执行 `git status` / `git diff` 等命令，由模型按需调用。

**仍差**：没有 `WatchService` 事件流，也没有自动 LSP / Git diff 摘要；变更感知是「每轮扫描一次」而非实时。

**差距 3：前端非服务端文件系统**

**现状**：`DirectoryPicker` 浏览的是服务端文件系统，用户选择的是**服务端磁盘上的目录**。这与用户直觉中的"打开本地项目"有偏差——类似 IDEA 的打开项目一般在客户端本地。

**Cursor 的做法**：客户端本地文件系统直接暴露给 Agent，无需通过网络 API 做服务端文件操作。

**改进方向**：

- 短期：在前端增加提示"选择的是服务端目录"，区分于本地目录
- 长期：如果部署模式变为客户端 Agent + 服务端 LLM 代理模式，则可利用 File System Access API 直接操作本地文件

**差距 4：工具调用进度与截断提示（已部分解决）**

**原现状**：AI 调用 `tree` 遍历大目录时，前端无任何进度指示器；大文件 `cat` 被截断后用户不知情。

**2026-11 已做**：`AgentActivityTimeline` 会为运行中的 step / tool 显示 loading 与耗时（`elapsedMs`），失败/取消有状态标签；工具结果摘要可折叠查看。

**仍差**：没有 0–100% 进度或中间输出流；截断内容没有独立查看入口，只在结果摘要里提示。

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

**差距 7：工作目录与知识库的割裂（已打通）**

**原现状**：工作目录（`work_dir`）和知识库（`kb_id`）是两个独立维度，不能同时激活。

**2026-11 已做**：`ChatController` 同时读取会话的 `work_dir` 和 `kb_id`，主聊天 Profile 同时暴露 `file` 与 `retrieve_kb` 动作；同一轮里 Agent 可以先查目录再检索知识库。`chat_sessions` 的 `kb_id` / `work_dir` 可并存。

**仍差**：RAG 命中片段不会标注「该内容也存在于工作目录」，两个来源在时间线上仍分开呈现。

---

### 3.2.4 与其他功能模块的关联

| 关联模块       | 关联方式                              | 影响                       |
| -------------- | ------------------------------------- | -------------------------- |
| 聊天对话       | `chat_sessions.work_dir` 存储         | 每个会话独立工作目录       |
| RAG 知识库     | `chat_sessions.kb_id` 独立字段        | 可与工作目录同时绑定；Agent 分别调 `file` / `retrieve_kb` |
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
| `ChatController.java`                  | 流式对话：绑定 WorkspaceContext / kbId / 附件，交给回合管道 |
| `WorkspaceContext.java`                | ThreadLocal + 跨线程传播                                     |
| `WorkspaceUtil.java`                   | Java NIO 文件操作引擎 + 路径安全检查                         |
| `WorkspacePromptBuilder.java`          | 路径规则 + 浅层目录摘要 + 最近修改文件                       |
| `FileTools.java`                       | 10 个 @Tool 方法，AI 文件读写工具集                          |
| `ShellTool.java`                       | 构建 / git / 脚本命令；超时、输出截断、危险命令拦截          |
| `ChatClientAssembler.java`             | ChatClient bean 配置 + 默认系统提示 + 默认工具               |
| `ContextPropagationConfiguration.java` | Micrometer 自动上下文传播                                    |
| `ChatSessionsService.java`             | 会话 CRUD，work_dir 存取                                     |
| `application.yaml`                     | `app.workspace.root` 配置                                    |
| `db/migration/V1__init_common_schema.sql` | `chat_sessions.work_dir` 列定义                           |

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
  └─ 后端（ChatController.chat → ChatUploadEnrichment）
      ├─ containsImageFile → 直接 400（图片前后端双拒，不再走 Media 兜底）
      ├─ buildFileList(docs) → "上传了以下文件：\n- xxx.md（6.4KB）"
      ├─ enrichPromptWithUploadedDocuments(docs)
      │   └─ 文件列表 + PDF/TXT 正文 → 拼进本轮 agentInput（只进本轮编排，不落 Memory）
      ├─ buildMemoryUserText(docs)
      │   └─ 文件列表 + 用户问题 → 回合末由 ChatTurnFinalizer 写入 spring_ai_chat_memory
      └─ ChatTurnPipeline → Agent Runtime（文件正文不进入 chat_memory）

刷新页面
  ├─ ChatBox.getMessages(chatId)
  │   ├─ 后端返回 user prompt（文件列表 + 用户问题）
  │   └─ 检测 "用户的问题：\n" 分隔符 → 拆分为两条 USER 消息
  └─ 渲染文件列表 + 用户问题（无 system 消息可过滤）
```

### 涉及代码文件

#### 后端

| 文件                  | 说明                                                         |
| --------------------- | ------------------------------------------------------------ |
| `ChatController.java` | `chat()` 入口：校验 ndjson、绑工作区 / kbId；图片直接 400 |
| `ChatUploadEnrichment.java` | 文件列表 + 正文抽取（单文件 50,000 字符截断）、Memory 短文本构建 |
| `ChatTurnFinalizer.java` | 回合成功时持久化短 USER + 最终 ASSISTANT |

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

**4.1 文档正文与 Memory 分离（关键决策）**

**背景**：早期把文档全文放进 user prompt，刷新后会显示整段原文，前端只能手动拆分。

**现方案**：正文只拼进本轮 `agentInput`（供 Agent 使用，不持久化）；`chat_memory` 只存「文件列表 + 用户问题」，前端刷新后看到的就是干净的文件名列表。

```
agentInput（仅本轮 Agent 可见，不落库）:
  上传了以下文件：
  - foo.pdf（2.3MB）

  以下为上传文档正文（供回答参考）：
  --- foo.pdf ---
  [正文，单文件最多 50,000 字符]

  用户的问题：
  总结这个文件

chat_memory（持久化，前端回显）:
  上传了以下文件：
  - foo.pdf（2.3MB）

  用户的问题：
  总结这个文件
```

**4.2 消息拆分与过滤**

| 技术                         | 目的                                                       |
| ---------------------------- | ---------------------------------------------------------- |
| `getMessages()` 中的拆分逻辑 | 检测 "用户的问题：\n" 分隔符，将后端合并的一条消息拆为两条 |
| `visibleMessages` computed   | 兜底过滤历史遗留的 system 消息（当前主链路已不再写 system） |

**4.3 图片：前后端双拒**

DeepSeek 文本模型不支持多模态。前端 `useFileUpload` 遇到 `image/*` 直接提示；后端 `ChatController` 再用 `containsImageFile` 校验并返回 400，避免「静默丢图」。不再走 `onErrorResume` 的 Media 兜底。

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

**当前 My-Chat 的位置**：Direct concatenation + 单文件 50,000 字符截断。对知识库类文档（数 KB 级别）完全够用。对长篇书籍、大文本日志会失效。

**升级路径**：不改当前做法的基础上，增加"当文件超过 N 字符时触发 RAG 流程"的分支，两种策略共存。

### 架构层面的优化方向

**当前架构的问题**

```
用户上传文件 → ChatController.chat()
  ├─ ChatUploadEnrichment 提取文本 → 拼进本轮 agentInput（字符串拼接）
  │                    ↑ 整个文件内容一次性写入，无分片、无检索
  ├─ Memory 只写文件列表 + 用户问题
  └─ ChatTurnPipeline → Agent Runtime
```

| 问题                    | 后果                                      |
| ----------------------- | ----------------------------------------- |
| 文件内容全部塞入本轮 prompt | 空耗 token，大文件很快撑爆 context window |
| 无分片检索              | 100 个文件无法只检索相关片段              |
| 无缓存                  | 相同文件每次上传都重新提取文本            |
| 文件正文不落库、不建索引 | 追问「刚才那份 PDF 第 3 节」时只能重传    |

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
- 无需截断（单文件 50,000 字符硬限制解除）
- AI 获得精准的相关片段而非全量噪音

**优化 2：文件内容缓存**

```
上传输入 → DocumentService.parseXxx()
         → 提取文本存入临时文件（或 Redis）
         → 返回 cacheKey

下次同一文件上传 → 命中 cacheKey → 跳过解析
```

**优势**：MD5/ETag 去重，大 PDF 避免反复解析。

**优化 3：消息持久化与 prompt 解耦（已部分完成）**

**原问题**：文档全文直接存到 `spring_ai_chat_memory.content`，刷新后显示整段原文。

**2026-11 状态**：正文已移出 Memory，只保留「文件列表 + 用户问题」；前端仍按 "用户的问题：\n" 拆分气泡。仍差的是附件元数据没有独立表，文件列表与用户问题仍是同一个 `content` 字符串。

**解耦方案**（进一步）：

```
user prompt 只存放用户文字 → "总结这个文件"
文件列表作为 metadata / 独立表附加到消息
chat_memory.content = "总结这个文件"（干净）
前端通过 metadata 渲染文件列表
```

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
| P0     | 当前做法 + 50,000 字符截断（已有）          | —        | 小文件场景已可用       |
| P1     | **RAG 化文件处理**                          | 3-5 天   | 大文件、多文件场景质变 |
| P2     | 文件内容缓存（MD5 去重）                    | 1 天     | 重复文件零开销         |
| P2     | 消息与 prompt 解耦（`message_attachments`） | 2 天     | 前端不需手动拆分       |
| P3     | 懒加载检索 + FileTools 协同                 | 持续迭代 | Cursor 级别体验        |

---

### 与 Cursor / opencode 的差距分析

| 能力            | My-Chat                     | Cursor / opencode          | 差距            |
| --------------- | --------------------------- | -------------------------- | --------------- |
| **文件类型**    | PDF / txt / md（图片拒绝）  | 任意格式（含二进制）       | 需扩展类型      |
| **上传方式**    | 手动点击 + 文件选择框       | 拖拽 + 粘贴图片 + 文件选择 | 缺少拖拽        |
| **文件预览**    | 纯文本列表                  | 行内渲染图片 + PDF 预览    | 无预览          |
| **AI 感知文件** | 后端提取文本 + 工作区 FileTools | AI 直接读本地文件系统  | 无法读客户端本地 |
| **消息一致性**  | 文件列表 + 原问合并存 Memory，前端拆分 | 天然分离        | 架构层需优化    |
| **大小限制**    | 10MB                        | 因服务而异                 | 可放宽          |
| **错误提示**    | 后端友好提示                | 前端即时反馈               | 可减少往返      |

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

主聊天附件功能不涉及数据库结构变更。消息存储依赖 `spring_ai_chat_memory` 表（Spring AI 框架自动维护），回合轨迹另存 `chat_assistant_turns`：

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

## 对话模型管理

`LlmModelController` + `LlmModelService` 把「用哪个模型」从 yaml 搬到库里，设置页可增删改、测试连通、设默认：

- 表 `llm_model`；启动时 `LlmModelSeedRunner` 按 yaml 补种子行。
- 供应商预设 `LlmProviderPresets`：DeepSeek / OpenAI / 通义千问 / Ollama / 硅基流动 / 自定义，选供应商回填 Base URL 与常用模型提示。
- 读取时经 Caffeine 缓存（读多写少）；保存后刷新。
- API Key 解析顺序：**设置里显式填写的值** → 环境变量 `OPENAI_API_KEY` → `application.yaml`。库中 `llm_model.api_key` 留空表示跟随环境变量。
- `ChatClientRegistry` / `ChatClientAssembler` 按当前默认模型创建 ChatClient；工具、MCP、Memory 仍由组装器统一挂载。

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
  Bridge["DefaultMcpToolBridge"]
  Tolerant["TolerantMcpToolCallbackProvider"]
  Database["localhost:8102 database"]
  Crawler["localhost:8103 crawler"]
  Smithery["mcp.smithery.run/2625705206"]
  Exa["Exa Search"]

  ChatClient --> Bridge
  Bridge --> Tolerant
  Tolerant --> Database
  Tolerant --> Crawler
  Tolerant --> Smithery --> Exa
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
3. 设置环境变量 `SMITHERY_API_KEY`（不要写进仓库）。缺 key / 未启动 `mcp-server-database` **不会**阻止主应用启动；他机可设 `APP_MCP_ENABLED=false` 整模块关闭。
4. IDEA Reload Maven 后重启 `my-chat-server`。MCP 客户端代码在 `com.mychat.mcp`，经 `McpToolBridge` 挂到主干 ChatClient。

### 当前实现（已落地）

**1. [`application.yaml`](../my-chat-server/src/main/resources/application.yaml)**

```yaml
spring:
  ai:
    mcp:
      client:
        enabled: ${APP_MCP_ENABLED:true}
        initialized: false          # 惰性握手：listTools / 首次调用再 initialize
        request-timeout: 60s
        toolcallback:
          enabled: false            # 关闭 Spring AI 默认 Provider，只走自研 Tolerant
        streamable-http:
          connections:
            mcp-database-server:
              url: http://localhost:8102
              endpoint: /mcp
            mcp-crawler-server:
              url: ${MCP_CRAWLER_URL:http://localhost:8103}
              endpoint: /mcp
            smithery-toolbox:
              url: https://mcp.smithery.run
              endpoint: /2625705206
app:
  mcp:
    enabled: ${APP_MCP_ENABLED:true}
    database.enabled: ${APP_MCP_DATABASE_ENABLED:true}
    crawler.enabled: ${APP_MCP_CRAWLER_ENABLED:true}
    smithery:
      enabled: ${APP_MCP_SMITHERY_ENABLED:true}
      api-key: ${SMITHERY_API_KEY:}
      connection-name: smithery-toolbox
```

**2. [`com.mychat.mcp`](../my-chat-server/src/main/java/com/mychat/mcp/)**

| 类 | 职责 |
| --- | --- |
| `McpModuleConfiguration` | `app.mcp.enabled` 条件装配；注册 Smithery Bearer customizer、`McpToolBridge`、启动诊断 |
| `McpProperties` | 三个连接的启用开关、连接名、Smithery key |
| `DefaultMcpToolBridge` | 把启用的 `McpSyncClient` 工具挂到主干 ChatClient；按 `McpRuntimeSettingsService` 过滤 |
| `TolerantMcpToolCallbackProvider` | 单连接 listTools / call 失败只跳过，不拖垮主聊天 |
| `McpRuntimeSettingsService` | `mcp_connection_settings` 运行时开关，DB + 内存缓存，改完立即生效 |

Smithery 鉴权仍通过 `McpClientCustomizer<HttpClientStreamableHttpTransport.Builder>` 注入 Bearer；无 key 时只 warn。

**3. 本地 MCP**

- `mcp-server-database`（:8102）：PostgreSQL 元数据 / 只读 SQL，供 Cursor 与主聊天共用
- `mcp-server-crawler`（:8103，Python FastMCP）：`fetch_url` / `fetch_urls` / `extract_main` / `list_adapters`，补齐研究侧「只有标题 + URL」的正文缺口
- 云上 Compose 只常驻 crawler（`MCP_CRAWLER_URL=http://crawler:8103`），database / Smithery 默认关闭

**4. 运行时开关（2026-11 新增）**

设置页「MCP 工具管理」→ `GET /ai/settings/mcp-tools`、`PUT /ai/settings/mcp-tools/{id}`，落 `mcp_connection_settings`（`mcp-database-server` / `mcp-crawler-server` / `smithery-toolbox` / `web-search-exa`）。开关立即影响下一轮工具发现，不需要重启。

**5. 联网搜索兜底**

搜索不走 `mcp.smithery.run` 网关（该网关 listTools 500 / callTool 403），优先本机 `searchWeb` REST：Exa → Bocha → Tavily → DuckDuckGo；`EXA_API_KEY` 来自 Exa 控制台，不要填 Smithery 的 key。

**6. 前端**

不改。工具经 `/ai/normalChat/chat` 链路由模型按需选择；设置页只多一个 MCP 开关面板。

# Agent Runtime（通用编排底座）

2026-11 完成从「主聊天专用编排」到「场景化 Runtime」的迁移：主聊天、市场分析、角色扮演、模拟炒股共用 `DefaultAgentRuntime`；场景差异只在 Profile 与 Action/AnswerPolicy，不在 Runtime 里写 `switch`。

## 场景 Profile

`AgentScenarioProfile` 固化动作、工具白名单、预算与输出/质量策略；`ScenarioProfileRegistry` 启动时收集并拒绝重复场景，`AgentProfileConsistencyChecker` 校验动作与工具的引用一致性。

| 场景 | workflow | 动作 | 工具组 | 步数 / 工具调用 | 输出 |
| --- | --- | --- | --- | --- | --- |
| `MAIN_CHAT` | orchestrator-workers | general / file / search / shell / retrieve_kb | file / shell / web-search / mcp | 6 / 8 | text |
| `MARKET_ANALYSIS` | orchestrator-workers | market_research / general | web-search | 6 / 6 | market_analysis |
| `ROLEPLAY` | chain | roleplay | — | 4 / 0 | text |
| `INTERVIEWER` | chain | interview | — | 5 / 0 | interview_assessment |
| `PAPER_RESEARCH` | orchestrator-workers | paper_research / general | paper-market / paper-research-feed / paper-strategy / paper-note-batch / web-search / mcp-crawler | 8 / 10 | research_card_batch |
| `PAPER_TRADER` | chain | paper_trade_review / general | paper-decision / paper-decision-write / paper-trade-exec / paper-run / paper-market / paper-strategy | 5 / 8 | trade_decision_batch |
| `PAPER_WAREHOUSE_ANALYST` | chain | paper_warehouse_analyze / general | paper-market / paper-research-feed / paper-strategy / paper-note / web-search | 3 / 8 | text |
| `PAPER_TRADE_EVALUATOR` | chain | paper_trade_evaluate / general | paper-strategy / paper-decision / paper-run / paper-note / paper-market / paper-research-feed / mcp-crawler / web-search | 6 / 10 | stage_eval_batch |

## 工具安全等级

`ToolCatalog` 统一登记工具组与副作用等级：

- `READ_ONLY`：查询、检索、无副作用计算，可自动执行。
- `REVERSIBLE_WRITE`：研究卡片、决策落库等可恢复写入；需显式业务上下文。
- `HIGH_RISK_WRITE`：下单、外部发送等不可逆动作；进入闸门/approval 边界。

Profile 白名单与动作工具子集取交集。模拟炒股 Agent 不注册订单 / 账户写入 / 任意 SQL / `file` / 通用 MCP；订单执行只允许领域 Workflow 在 Java 风控后调用。

## 持久化与可观测（V9）

- `agent_run`：一次语义运行，含场景、Profile 版本、`business_ref`、`async_job_id`、幂等键、状态、checkpoint、token。
- `agent_run_step`：WORKFLOW / ACTION / MODEL / TOOL / GUARDRAIL / HANDOFF / EVALUATOR / APPROVAL 等 span；保存脱敏摘要、耗时、错误。
- `agent_approval`：高风险动作的请求与决定分离（PENDING / APPROVED / REJECTED / EXPIRED / CANCELLED），恢复时核对批准状态。
- 公开时间线只经 `PublicProgressAdapter` 输出脱敏摘要；原始 Prompt、reasoning、完整工具结果不进公开事件。
- Actuator 暴露 `agent.runtime.runs`、`duration`、`tokens`、`tool.errors`、`budget.exhausted` 等指标。
- `StructuredOutputRepairService` 对 `PAPER_RESEARCH` / `PAPER_TRADER` 等类型化输出只修结构与字段类型，不新增事实；修复后仍由领域适配器回传业务 Workflow。

## 读代码入口

`agent/runtime/DefaultAgentRuntime.java` → `StructuredDecisionEngine` / `WorkflowRunner`（orchestrator-workers / chain / routing / evaluator-optimizer）→ `agent/action/*ActionHandler` → `agent/tool/ToolCatalog`。领域侧从 `PaperAgentRuntimeAdapter` 进入，`businessRef` = 对应 `paper_*_run` 的 ID。

# 输出后处理

## 可观测性时间线总结与思考回显规划

### 一句话结论

主聊天已统一走 **NDJSON 事件流**（`format` 只接受 `ndjson`），实时展示 route / step / tool / KB 引用时间线，并以本地表 **`chat_assistant_turns`** 按轮持久化 `parts`，刷新后可回放。思考尚未作为时间线步骤：`thinking_delta` 协议已定义、`ReasoningContentExtractor` 已建，但默认 `thinking.type: disabled` 且流式链路未接线，当前回合通常没有思考事件；旧 `[THINKING]` 标签与 `format=plain` 路径均已退役。

```text
用户 → ChatController(?format=ndjson)
     → ChatTurnPipeline（emit_route / load_dialogue / assemble_context / orchestrate / stream_final / quality_loop）
     → OrchestrateStage → Agent Runtime（旁路 tool_call / tool_result / public_progress）
     → text_delta / step / route / done（thinking_delta 协议已定义，尚未接线）
     → 前端 AgentActivityTimeline + Markdown
     → 回合结束 Finalizer 异步写入 chat_assistant_turns
     → GET /ai/history/getMessages 合并 parts 回放
```

---

### 三周做了什么

**第 1 周：协议 + 后端旁路**

| 项     | 内容                                                         |
| ------ | ------------------------------------------------------------ |
| 传输   | 同一 POST 上的 **NDJSON**（`application/x-ndjson`）；主聊天仅接受 `format=ndjson`，其他格式直接 400 |
| 采集   | `ObservabilityStreamAdvisor`（旁路 tool_call / tool_result）+ 管道 `ChatTurnEmitter`（route / step / text_delta） |
| 事件   | `thinking_delta`（已定义，默认未发）/ `text_delta` / `tool_call` / `tool_result` / `step` / `route` / `error` / `done` |
| 关键类 | `ChatStreamEvent`、`ChatStreamEventWriter`、`ChatController`、`ChatTurnPipeline` |
| 注意   | Spring AI 2.0 已删除 `streamToolCallResponses`，不可再依赖   |

**第 2 周：前端时间线**

| 项   | 内容                                                         |
| ---- | ------------------------------------------------------------ |
| 消费 | `streamChat` 固定普通聊天 `?format=ndjson`；行缓冲解析       |
| 状态 | `useChatStream` 归约 `streamingParts` / `streamingThinking` / 正文 |
| UI   | `AgentActivityTimeline` 渲染 **route / step / tool**，KB 步骤带 `kbScope` 与引用标签；`ChatBox` 去掉错误 `THINKING_START` 解析 |
| 范围 | 主聊天全链路；知识库召回测试也在页面内，但不走聊天时间线 |

**第 3 周：回放落库**

| 项   | 内容                                                         |
| ---- | ------------------------------------------------------------ |
| 表   | `chat_assistant_turns`（一轮一行：`parts` JSONB + `thinking` + `assistant_ordinal`） |
| 写入 | 流结束异步归约落库；闲聊无轨迹可跳过                         |
| 读取 | `getMessages` → `ChatMessageVO`，按 ordinal（文本兜底）挂 `parts` |
| 约束 | **不改** `spring_ai_chat_memory`；删会话级联清回合表         |
| 踩坑 | PG `jsonb` 不能用默认 `JacksonTypeHandler`（varchar）；需 `PGobject` TypeHandler；`postgresql` 依赖须 compile |

详细设计见 [docs/迭代规划.md](./迭代规划.md) 与 [docs/Agent Runtime运维与Profile规范.md](./Agent%20Runtime运维与Profile规范.md)；本文件只做索引级总结。

---

### 当前能力边界（已知未做）

| 未做                     | 说明                                      |
| ------------------------ | ----------------------------------------- |
| 思考与工具交错展示       | 见下文待实现；`ReasoningContentExtractor` 已建类但未接入 |
| Agent Runtime 运行详情页 | `agent_run` / `agent_run_step` 已有数据与指标，前端暂无独立 Run 详情页 |
| OTEL / Spring AI Session | 刻意不做，Memory 旁路表已够 UI 回放       |

---

### 待实现：思考模式回显（多厂商 + 时间线交错）

曾尝试过实现一次，但效果不好，可能放弃这一块。

**2026-11 状态**：`ReasoningContentExtractor` 已实现「四来源提取 + 累计全文 suffix diff」，但尚未被流式主路径调用；时间线组件仍过滤 `type==='thinking'`；`application.yaml` 中 `thinking.type: disabled`，默认无思考流。

#### **4.1 动机**

早期 `toThinkingResponse` 用 `[THINKING]…[/THINKING]` 适配 DeepSeek 思考模式；主路径已改 NDJSON 后，该拼接**实质无用**。  
多家模型仍会输出思考，但字段名不一（`reasoningContent` / `thinking` / `reasoning` / `getReasoningContent()`），当前缺口：

1. 提取器已能读四类来源，但流式链路未调用；
2. **含 `toolCalls` 的帧仍不作为 thinking 事件发出**，工具前思考进不了时间线；
3. 时间线组件过滤掉 `type==='thinking'`，思考只在气泡顶栏折叠；
4. `application.yaml` 中 `thinking.type: disabled`，默认无思考流。

#### **4.2 目标**

1. 退役主聊天 plain 路径的 `[THINKING]` 拼接。  
2. 统一提取多厂商 reasoning → 协议事件 `thinking_delta` / part `type:"thinking"`。  
3. 时间线顺序：`thinking → tool → thinking → tool → … → 正文`。  
4. 历史回放：`parts` 内 thinking 片段保持交错顺序（**不必改 schema**）。

#### **4.3 设计要点**

**提取层**（`ReasoningContentExtractor` 已存在，待接入流式主路径）：

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
| 事件 DTO  | `my-chat-server/src/main/java/com/mychat/common/ChatStreamEvent.java` |
| Advisor   | `my-chat-server/src/main/java/com/mychat/utils/ObservabilityStreamAdvisor.java` |
| 回合管道  | `my-chat-server/src/main/java/com/mychat/service/agent/pipeline/`（`ChatTurnPipeline`、`ChatTurnFinalizer`、`stage/`） |
| 流式入口  | `my-chat-server/src/main/java/com/mychat/controller/ChatController.java` |
| 归约落库  | `my-chat-server/src/main/java/com/mychat/utils/TurnPartsReducer.java`、`service/chat/impl/ChatAssistantTurnServiceImpl.java` |
| 历史合并  | `my-chat-server/src/main/java/com/mychat/controller/ChatHistoryController.java` |
| 前端流    | `my-chat-vue3/src/utils/streamChat.ts`、`composables/useChatStream.ts` |
| 时间线 UI | `my-chat-vue3/src/components/AgentActivityTimeline.vue`、`ChatBox.vue` |
| DDL       | `my-chat-server/src/main/resources/db/migration/V1__init_common_schema.sql`（`chat_assistant_turns`、`chat_session_summary`） |

# 质量监测系统

## 召回测试

对齐 Dify「检索测试」、FastGPT「搜索测试」：给**知识库配置者**看命中片段和分数，不进聊天、不调 LLM。**2026-11 已实现**：`POST /ai/knowledge-base/documents/retrieve-test` + `KnowledgeStore.vue` 的「召回测试」面板。

### 边界（贴市场、不膨胀）

- **做**：选中知识库 → 输入模拟问题 → 列出片段正文、分数、来源文件名；可用该库已保存的 topK/阈值，或在测试页临时改这两项（不写库，对应 Dify「仅当前测试生效」）。
- **不做**：检索历史表、混合检索/Rerank、父子分段、新路由、改 `V1__init_common_schema.sql`（无新表无新列）。

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

与问答共用过滤表达式 `kbId == '{id}'`（入库 metadata 已是 `kbId` / `filename` / `documentId`，见 [`DocumentService`](../my-chat-server/src/main/java/com/mychat/service/knowledge/DocumentService.java)）。

### 后端（已落地）

**1. 检索请求构造**

[`KbSearchRequests`](../my-chat-server/src/main/java/com/mychat/service/knowledge/KbSearchRequests.java) 的 `filtered(kbId, topK, threshold)` 返回已设 `topK`、`similarityThreshold`、`filterExpression` 的 `SearchRequest.Builder`，召回测试与聊天检索共用，避免过滤条件分叉。

**2. `KnowledgeRetrievalService`**

- 知识库必须存在
- `query` 去空白后非空，长度上限 1000
- `topK` / `threshold` 未传则用该库已存值（`KnowledgeBaseSettings.topKOrDefault` / `thresholdOrDefault`）；传入则只做范围校验（1–20、0–1），**不 update 知识库**
- `vectorStore.similaritySearch(builder.query(query).build())`
- 映射为 DTO：`text`、`score`（`Document.getScore()`，没有则 metadata）、`filename`、`documentId`；保持 VectorStore 返回顺序
- 空列表合法（阈值滤光），不当错误

**3. API / DTO**

- `POST /ai/knowledge-base/documents/retrieve-test`：Body `kbId`、`query`，可选 `topK`、`similarityThreshold`；响应含实际使用的参数与 `hits[]`
- 另有两个配套接口：`GET /documents/chunks`（只读分段预览）、`POST /documents/reindex`（按当前切分参数重新向量化，异步）

### 前端（已落地）

放在 [`KnowledgeStore.vue`](../my-chat-vue3/src/views/knowledgeStore/KnowledgeStore.vue)，不新开路由：

- 顶栏「召回测试」与「设置 / 上传」并列；未选库时禁用
- 「文档表」/「召回面板」切换；切库时回到文档表并清空结果
- 面板：textarea +「测试」；旁侧 topK、阈值，打开时从 `currentKb` 拷贝，文案注明**仅本次有效，不保存到设置**
- 结果：无命中 `el-empty`；命中列表显示分数、文件名、正文（过长折叠）
- 文档表另有「分段」「重新向量化」操作

---

## 写盘质量环（Evaluator-Optimizer）

主聊天在 `stream_final` 之后可选执行质量环，用于「Agent 写了文件 → 自动评审 → 按反馈修订」。

- **触发**：`POST /ai/normalChat/chat` 的 `qualityLoop` 参数，默认 `true`；显式 `false` 关闭。`criteria` 可覆盖评价标准。
- **路径解析**：`WritePathExtractor` 先从本轮 write 工具事件里找路径，找不到再尝试从用户原问里提示路径；仍解析不到则跳过（只记日志，不报错）。
- **执行**：`QualityLoopStage` 在 `boundedElastic` 上调用 `AgentEvaluatorOptimizerService.evaluateOptimizeExplicit`，逐轮推进 `step` 事件（`evaluate_optimize`）并在文末追加「已通过 / 未完全通过 + 原因」摘要。
- **失败隔离**：质量环异常只推一条失败 step，不打断回合，也不影响 Memory 落库。
- **相关代码**：`service/agent/pipeline/stage/QualityLoopStage.java`、`agent/quality/MainChatQualityPolicy.java`、`service/agent/quality/AgentEvaluatorOptimizerService.java`、`service/agent/workflow/EvaluatorOptimizerWorkflow.java`、`utils/WritePathExtractor.java`。

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
| Redis / RabbitMQ / Celery | 单机过重；配置类读缓存改用进程内 Caffeine（Spring Cache），不上 Redis |
| 聊天同款 NDJSON `Flux`    | 浏览器 `EventSource` 吃不了，且绑在某次聊天请求上    |
| 标准 SSE + 任务表         | 与 Element Plus 通知中心接法一致；离开业务页连接仍在 |

和本仓库的契合点：

- PostgreSQL + MyBatis-Plus，风格对齐 `knowledge_base`
- Tomcat 已开虚拟线程；**后台任务仍用有界池**，避免以后 embedding 打满 API
- `ragClient` 会解包 `Result<T>` 且 timeout=25s，**不能**订 SSE

---

### @.1.3 数据模型

表 [`async_job`](../my-chat-server/src/main/resources/db/migration/V1__init_common_schema.sql)：

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
| [`AsyncJobService.submit`](../my-chat-server/src/main/java/com/mychat/job/AsyncJobService.java) | insert `PENDING`，立刻 `dispatch`，HTTP 不等待               |
| [`AsyncJobDispatcher`](../my-chat-server/src/main/java/com/mychat/job/AsyncJobDispatcher.java) | 独立 Bean 上 `@Async("jobExecutor")`，避免 Service 自调用导致异步失效 |
| [`JobHandler`](../my-chat-server/src/main/java/com/mychat/job/JobHandler.java) | `type()` + `execute(job)`；成功返回，失败抛异常              |
| [`JobEventHub`](../my-chat-server/src/main/java/com/mychat/job/JobEventHub.java) | 进程内 `SseEmitter` 扇出；15s 注释心跳                       |
| [`StaleJobRecovery`](../my-chat-server/src/main/java/com/mychat/job/StaleJobRecovery.java) | 启动时把卡住超过 `app.jobs.stale-minutes`（默认 10）的 PENDING/RUNNING 标 FAILED |
| [`AsyncConfiguration`](../my-chat-server/src/main/java/com/mychat/config/AsyncConfiguration.java) | `@EnableAsync` + `@EnableScheduling`；池 core=2 / max=4 / queue=100 / CallerRuns |

调度步骤（`dispatch`）：

1. CAS：`PENDING → RUNNING`（`update=0` 则跳过，防重复）
2. SSE 推 `RUNNING`
3. 按 `job_type` 找 Handler；没有则 `FAILED`
4. `execute`；异常 → `FAILED` + `error_message`
5. SSE 推终态

**HTTP**

[`JobController`](../my-chat-server/src/main/java/com/mychat/controller/JobController.java) `/ai/jobs`：

| 方法 | 路径      | 说明                                                         |
| ---- | --------- | ------------------------------------------------------------ |
| GET  | `/active` | `Result<List<AsyncJobVO>>`，刷新后补未完成任务               |
| GET  | `/events` | **SSE**，`event: job`，data 为 VO JSON；**不是** `Result` 信封 |

验收接口 `POST /ai/jobs/demo` 与 `DemoSleepJobHandler` **已删除**。

**长连接超时（必配）**

否则 SSE 几十秒被掐断：

- [`application.yaml`](../my-chat-server/src/main/resources/application.yaml)：`spring.mvc.async.request-timeout: 24h`
- [`vite.config.ts`](../my-chat-vue3/vite.config.ts) `/rag` 代理：`timeout` / `proxyTimeout` = 24h

`JobEventHub` 另设 Emitter 24h + `Cache-Control: no-cache`、`X-Accel-Buffering: no`。

---

### @.1.5 前端

| 文件                                                         | 职责                                                         |
| ------------------------------------------------------------ | ------------------------------------------------------------ |
| [`stores/notify.ts`](../my-chat-vue3/src/stores/notify.ts) | 先 `listActive`，再 `EventSource('/rag/ai/jobs/events')`；仅终态弹窗 |
| [`App.vue`](../my-chat-vue3/src/App.vue)                  | `onMounted(connect)`，路由切换不断线                         |
| [`api/jobs.ts`](../my-chat-vue3/src/api/jobs.ts)          | REST 用 `ragClient`；流用原生 `EventSource`                  |
| [`utils/notifySound.ts`](../my-chat-vue3/src/utils/notifySound.ts) | 双音门铃连响三遍（约 2s）                                    |

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

后端：`entity/po/AsyncJob.java`、`mapper/AsyncJobMapper.java`、`job/AsyncJobService.java`、`job/*`、`controller/JobController.java`、`config/AsyncConfiguration.java`

前端：`types/jobs.ts`、`api/jobs.ts`、`stores/notify.ts`、`utils/notifySound.ts`、`App.vue`

## @.2 部署与运维（Compose + Jenkins）

2026-11 已落地阿里云 CentOS 7.9 的整站部署，细节见 [部署与运维指引.md](./部署与运维指引.md) 与 [`deploy/`](../deploy/)。

- **拓扑**：复用宿主机 `pg`（pgvector，数据在 `/data/pgdata`）；Compose 跑 `app`（JDK 25）+ `nginx`（静态 + `/rag/` 反代）+ `crawler`（Python 爬虫 MCP，仅内网 8103）；Jenkins / Dozzle / Adminer 为可选 profile。
- **发布**：推 GitHub → Jenkins Pipeline（`deploy/Jenkinsfile`）串行 build crawler / app / nginx → `up -d` → `curl http://nginx/` 健康检查。
- **密钥**：只在服务器 `/data/my-chat/.env`（`chmod 600`），Jenkins 用 `--env-file` 读取，不入库、不写进 `deploy/.env.example`。
- **时区**：容器与定时统一 `Asia/Shanghai`；应用内 `paper_schedule_settings.zone_id` 解释 cron，避免 UTC 云主机把 15:35 跑成 23:35。
- **结构**：Flyway 在 app 启动时自动迁移到最新版本；云上 database MCP / Smithery 默认关闭，只常驻 crawler。
- **排障**：Dozzle 看容器日志（SSH 隧道 8888）、Adminer 查库（8889）；内存 4G 机器不上 Redis/ES，缓存用 Caffeine。

# 模拟炒股（Paper Trade）

> 核对依据：当前代码 + Flyway `V3`–`V14`（2026-11-20）。虚拟盘，不接实盘券商；行情取东财收盘价做市价撮合。

## 定位与入口

大厅功能包「模拟炒股」：自建虚拟账本 + 收盘价市价撮合，叠加研究卡片、价值分层、AI 操盘、风控闸门、五条定时工作流与可视化轨迹。属于自用扩展，不是项目主目标。

| 层 | 路径 |
| --- | --- |
| 前端 | `my-chat-vue3/src/apps/paper-trade/`：`/app/paper-trade` → `positions` / `history` / `settings` / `workflow` |
| 后端包 | `com.mychat.apps.papertrade`（ledger / trader / research / risk / warehouse / evaluator / schedule / workflow / audit / mail / tools / agent） |
| API | `/ai/apps/paper-trade`（`PaperTradeController` + `PaperTradeAdminController`） |
| 配置 | `app.paper-trade.*`（`PaperTradeProperties`）+ `paper_schedule_settings` / `paper_risk_settings` |
| 行情 | `com.mychat.apps.market` 的 `MarketQuoteClient` / `EtfPremiumClient` 等 |
| 编排 | `PaperAgentRuntimeAdapter` → 通用 `AgentRuntime`；场景 `PAPER_RESEARCH` / `PAPER_TRADER` / `PAPER_WAREHOUSE_ANALYST` / `PAPER_TRADE_EVALUATOR` |
| 异步任务 | `paper_trade_research` / `paper_trade_ai_round` / `paper_trade_warehouse_analyst` / `paper_trade_warehouse_cleanup` / `paper_trade_evaluator` |
| 价值等级标准 | [模拟炒股-研究价值等级标准.md](./模拟炒股-研究价值等级标准.md) |

前端四页：`PositionsView`（账户指标 / 看板 / 下单 / 持仓 / 决策 / 订单）、`HistoryView`（5 类历史资源 CRUD + 单票补资料 + SQL 导出）、`AccountSettingsView`（常规设置 / 定时工作流 / 策略与风控 / 危险操作）、`WorkflowView`（五条只读工作流蓝图 + 最近一轮高亮）。

## 主流程（简图）

```text
研究搜集(research) → paper_research_note（kept + value_tier）
        ↓
票仓分析员(analyst) → 深搜后 updateValueTier（1/2/3）
        ↓
AI 操盘(trader) ← paper_strategy + 账户/持仓/行情 + kept 卡片
        ↓
paper_ai_decision → 风控闸门(risk) → 人工确认(可选) → 价值等级校验
        ↓
ledger 市价撮合 → paper_order / paper_position / paper_cooldown
        ↓
阶段重置/枯竭 → 交易评估员写 paper_stage_summary + 发邮件
```

手动下单走同一套 `PaperTradeService.placeOrder`；自动出场（止盈/止损/到期）`source=EXIT`。

## 五条定时工作流

运行时以 `paper_schedule_settings` 为准，`PaperScheduleManager` 启动时读取并挂 Spring `ThreadPoolTaskScheduler` cron；账户设置保存后 `reschedule()` 动态重挂，不引入 Quartz。

| 工作流 | 默认 cron（Asia/Shanghai） | 默认开关 | 说明 |
| --- | --- | --- | --- |
| 搜集员 research | `0 0 9 * * *` | 关（yaml `research-schedule-enabled: false`） | 翻页选候选 → 行情/流动性/相对低点闸门 → Agent 写研究卡片 |
| 交易员 trader | `0 35 15 * * MON-FRI` | 开 | 先扫规则出场，再组上下文调模型，逐条过闸门；收盘价撮合 |
| 票仓分析员 analyst | `0 0 18 * * *` | 开 | 取 `value_tier=2`（最多 batch 条），每票最多 3 步深搜后 `updateValueTier` |
| 票仓清理 cleanup | `0 0 23 * * *` | 开 | 无 AI：0 跳过；1→2；2 且滞留 ≥ N 天→3；3 硬删 |
| 交易评估员 evaluator | `0 0 10 * * SAT` | 开 | 周复盘：写阶段总结、发建议邮件；交易员「资金枯竭」时也会唤醒它 |

共同约束：

- 同类工作流用 `PaperRunLifecycle.requireNoActive` 拒绝重叠活动轮次；重复触发只记日志。
- 每轮写 `framework_trace`（domainSteps + agent 轨迹），供 `WorkflowView` 回放；失败截断错误到 1000 字。
- 定时落点用 `schedule-zone` 解释（云主机常为 UTC）；交易员如果配在工作日 09:00–15:00，启动时只 warn 不阻止。
- 评估员发信未配 SMTP / 收件人为空时返回 `SKIPPED`，正文落日志；`paper_trade_evaluator_run.email_status` 记录结果。

## 价值等级（0–3）

`paper_research_note.value_tier`（V11，SMALLINT 默认 2）把「观察名单」做成交易就绪漏斗；数字越小，对当前阶段资本配置的优先级越高。完整标准见 [模拟炒股-研究价值等级标准.md](./模拟炒股-研究价值等级标准.md)。

| 等级 | 含义 | 谁写入 |
| --- | --- | --- |
| 0 | 已持仓 | 仅系统在 BUY 成交后写入 |
| 1 | 应该买入 | 搜集员（可人工改） |
| 2 | 继续观察 | 搜集员 / 全仓 SELL 成交后系统回写 |
| 3 | 移出票仓 | 搜集员（`kept=false` 时强制倾向 3） |

关键规则：

- 交易员**非持仓买入**硬门槛：卡片 `kept=true AND value_tier=1`，否则跳过原因 `VALUE_TIER_NOT_BUY`。
- 搜集员 upsert：已持仓（0）不覆盖；`kept=false` → 3；输出 1/2/3 之外或缺失 → 2。
- 票仓清理：0 跳过；1→2；2 且 `当前日 - 创建日 ≥ warehouse_tier2_retain_days`（默认 2）→ 3 并 `kept=false`；3 硬删。
- 票仓分析员只能把 2 调成 1/2/3，工具 `updateValueTier` 禁止改 0；`1/2` 会补 `kept=true`，`3` 会置 `kept=false`。

## 策略半可编辑与结构化风控

`paper_strategy` 拆成两段：

- `content`：平台锁定区（服务端按当前风控重算后与自定义区拼接），包含输出 JSON 格式、整手、`value_tier`、工具调用顺序等硬约束。
- `custom_content`：用户可编辑的身份、风格、低点/企稳叙事与原则口吻（V13 新增）。

`paper_risk_settings`（V13，单行 `default`）把运行时风控从 yaml 搬到页面，保存策略时同一请求写入，并立即影响闸门与锁定文案：

| 组 | 字段（默认） |
| --- | --- |
| 仓位 | `maxPositions=2`、`maxSingleAllocation=0.95`、`riskBudgetPct=0.02`、`riskBudgetTightPct=0.01` |
| 熔断 | `drawdownRestrictPct=0.08`、`lossStreakLimit=3`、`buyPauseDays=2` |
| 执行 | `reviewConfidenceThreshold=0.55`、`autoExecute=true` |
| 冷却 | `cooldownDaysStock=2`、`cooldownDaysEtf=1` |
| 出场 | 股票 `4%/3%/10天`、ETF `3%/2%/15天`、QDII `4.5%/3%/10天` |

## 研究卡片与单票补资料

`paper_research_note` 按代码一行，字段含 `kept` / `watchlist`（V7）/ `value_tier`（V11）/ `story` / `risks` / `fit_score` / `evidence_score` / `signal_tags` / `source_tier`（V8）/ `sources_json` / `quote_summary` / `reject_reason` / `run_id`。

- **整轮搜集**：翻页选候选 → 行情闸门（ST/停牌/涨跌停/上市天数/20 日均额）→ 相对低点（低于 MA20 或 60 日回撤 ≤ -8%）→ 公告/新闻/板块 + 爬虫正文（全轮共享预算）→ Agent 调 `upsertResearchCards`，Java 兜底解析 `ResearchCardBatch` 并落库。
- **单票补资料**（2026-11 新增）：`POST /ai/apps/paper-trade/admin/research-notes/{symbol}/refresh` → 提交 `paper_trade_research` 任务（payload `{symbol}`）。与整轮差异：不要求相对低位、成交额、上市天数；但无行情 / ST / 停牌 / 涨跌停会让该轮失败，原卡片保持不变。HistoryView 的研究卡片行内有「补资料」按钮并轮询轮次状态。
- **自选**：人工新建卡片 `watchlist=true`，AI 新建为 false；`includeWatchlist` 控制是否让自选优先进入交易员候选。

## 工具隔离、审计与执行边界

- 四个模拟炒股 Profile 只暴露各自工具子集；**不注册**账户写入 / 任意 SQL / `file` / 通用 MCP。下单只经 `paper-trade-exec` 工具（`placePaperOrder`）或 Java 兜底，二者都会先过领域闸门。
- 交易员工具把「决策落库」与「下单」分开：`recordAiDecisions` 只写 `paper_ai_decision`，`placePaperOrder` 才触发 Java 闸门与 ledger 撮合；模型也可只输出 `TradeDecisionBatch` 由 Java 兜底。
- 搜集员写库优先走 `upsertResearchCards(runId, cardsJson)`；工具没落库时 Java 兜底并 warn。
- **写库审计**：`PaperAiToolAuditAspect` 拦截 `com.mychat.apps.papertrade.tools` 中 `create/update/delete/upsert/record/place` 开头的 `@Tool`，仅在 `AgentToolAuditContext` 激活时写 `paper_ai_tool_audit`（场景、agentRunId、工具名、表名、操作、symbol、参数/结果截断、成功与否）。
- 高风险动作（下单）仍只允许领域 Workflow 在 Java 风控后调用；Agent 不直接持有 ledger 写接口。

## 表按业务线分类

### 1. 账本 ledger（V3 / V4）

| 表 | 作用 |
| --- | --- |
| `paper_account` | 虚拟账户；V4 增 `peak_equity` / `max_drawdown` / `loss_streak` / `buy_pause_until` / `stage_started_at` |
| `paper_position` | 持仓（T+1 `available_qty`、止盈止损、最长持有） |
| `paper_order` | 市价单 FILLED/REJECTED；V4 增 `realized_pnl` / `exit_reason`；`source`=MANUAL/AI/EXIT |
| `paper_cooldown` | 卖出后冷却，到期前不可再买该标的 |

### 2. AI 操盘 trader（V3，轨迹列 V5，策略 V13）

| 表 | 作用 |
| --- | --- |
| `paper_strategy` | 策略正文；V13 增 `custom_content`（用户可编辑区） |
| `paper_ai_run` | 一轮 AI 运行；关联 `async_job`；V5 增 `framework_trace` |
| `paper_ai_decision` | 结构化决策 BUY/SELL/HOLD/FLAT；`exec_status` 控制执行/跳过/人工 |

### 3. 信息搜集 research（V5，V7 / V8 / V11）

| 表 | 作用 |
| --- | --- |
| `paper_research_run` | 搜集一轮（只写卡片、不下单）；单票补资料复用此表 |
| `paper_research_note` | 按代码一行研究卡片；V7 自选、V8 评分/信源、V11 价值等级 |

### 4. 风控、票仓与评估（V4 / V12 / V13 / V14）

| 表 | 作用 |
| --- | --- |
| `paper_stage_summary` | 账户重置前阶段成绩（胜率、回撤、费用、总结文案） |
| `paper_risk_settings` | V13 运行时风控单行（仓位/熔断/冷却/出场） |
| `paper_warehouse_cleanup_run` | 票仓清理一轮（无 AI，统计 + 轨迹） |
| `paper_warehouse_analyst_run` | 票仓分析员一轮（筛选数 / 调整数 / 轨迹） |
| `paper_trade_evaluator_run` | 交易评估员一轮（触发类型、阶段总结、邮件状态、轨迹） |
| `paper_ai_tool_audit` | Agent 工具写库审计（仅写操作） |

### 5. 跨模块关联（非 paper_ 前缀）

| 表 | 关系 |
| --- | --- |
| `async_job` | 五条工作流异步执行与 SSE 通知；同类轮次用 `requireNoActive` 防重叠 |
| `agent_run` / `agent_run_step` | 模拟炒股 Agent 的语义执行与轨迹；`business_ref` 关联轮次 ID |
| `market_forecast` | 属「股市分析」功能包（V2），**不是**模拟盘账本；行情由 `MarketQuoteClient` 实时拉取 |

默认账户、默认策略、风控单行与定时配置由启动时 `PaperAccountSeedRunner` / `PaperStrategySeedRunner` / `PaperScheduleSeedRunner` 等写入或补种，不把业务种子写进 Flyway。

## Flyway 增量历史（本模块）

| 版本 | 文件 | 内容 |
| --- | --- | --- |
| V3 | `V3__init_paper_trade_schema.sql` | 账本 + 策略 + AI run/decision |
| V4 | `V4__paper_trade_risk_gates.sql` | 账户/订单风控列 + `paper_stage_summary`；空 default 账户种子改 2 万 |
| V5 | `V5__paper_research.sql` | research 表 + `paper_ai_run.framework_trace` |
| V6 | `V6__paper_schedule_settings.sql` | 搜集员/交易员定时配置（账户设置可改、动态重挂） |
| V7 | `V7__paper_watchlist.sql` | 研究卡片自选 + `include_watchlist` |
| V8 | `V8__paper_research_sources.sql` | 工具检索开关 + 契合/证据分/信号标签/信源层级 |
| V11 | `V11__paper_research_value_tier.sql` | 价值等级（0–3）+ 索引 + 历史回填 |
| V12 | `V12__paper_warehouse_workflows.sql` | 清理/分析员定时 + 两轮次表 + 工具审计表 |
| V13 | `V13__paper_strategy_partial_edit.sql` | `custom_content` + `paper_risk_settings` |
| V14 | `V14__paper_trade_evaluator.sql` | 评估员定时/收件人 + `paper_trade_evaluator_run` |

> V9（Agent Runtime 持久化）与 V10（MCP 连接开关）是跨模块迁移，不属于本模块但被工作流复用。

后续结构变更只新增更高版本 `V{n}__*.sql`，禁止改已执行脚本。

## API 概览

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/ai/apps/paper-trade/account`、`/positions`、`/orders` | 账户 / 持仓 / 最近订单 |
| POST | `/ai/apps/paper-trade/orders` | 手动市价单；拒单仍返回订单体 |
| POST | `/ai/apps/paper-trade/account/reset` | 重置账户（保留 AI / 研究 / 阶段历史） |
| POST | `/ai/apps/paper-trade/account/wipe` | 注销训练（清空业务数据、保留策略） |
| PUT | `/ai/apps/paper-trade/account/initial-cash` | 修改初始资金 |
| GET/PUT | `/ai/apps/paper-trade/strategy` | 读取 / 保存策略 + 结构化风控 |
| GET/PUT | `/ai/apps/paper-trade/schedule` | 读取 / 保存五条工作流并动态重挂 |
| POST | `/ai/apps/paper-trade/ai/run` | 提交 AI 操盘一轮 |
| GET | `/ai/apps/paper-trade/ai/runs`、`/ai/runs/{id}`、`/ai/decisions` | 轮次与决策 |
| POST | `/ai/apps/paper-trade/ai/decisions/{id}/confirm`、`/dismiss` | 人工确认 / 忽略 |
| GET | `/ai/apps/paper-trade/research/notes` | 已留研究卡片 |
| POST | `/ai/apps/paper-trade/research/run` | 提交整轮搜集 |
| GET | `/ai/apps/paper-trade/research/runs/{id}` | 搜集轮次状态 |
| POST | `/ai/apps/paper-trade/crawler/probe` | 本地爬虫 MCP 连通性探测 |
| POST | `/ai/apps/paper-trade/warehouse/cleanup/run`、`/analyst/run` | 票仓清理 / 分析员 |
| POST | `/ai/apps/paper-trade/evaluator/run` | 交易评估员（周更） |
| GET | `/ai/apps/paper-trade/workflows` | 五条工作流蓝图 + 最近一轮 |
| GET/POST/PUT/DELETE | `/ai/apps/paper-trade/admin/*` | 阶段总结 / 决策 / 搜集轮 / 研究卡片 CRUD；研究卡片 `/{symbol}/refresh` 单票补资料 |
| GET | `/ai/apps/paper-trade/admin/export/{table}` | 白名单表全量 INSERT.sql |

## 关键代码索引

| 区域 | 路径 |
| --- | --- |
| API | `apps/papertrade/PaperTradeController.java`、`PaperTradeAdminController.java` |
| 管理端 | `PaperTradeAdminService.java`（历史 CRUD、wipe、SQL 导出服务） |
| 账本 | `ledger/PaperTradeService.java`、`ledger/PaperQuoteService.java` |
| 策略 / AI | `trader/PaperStrategyService.java`、`PaperAiTraderService.java`、`PaperAiContextBuilder.java` |
| 搜集 | `research/PaperResearchService.java`、`PaperStoryFeed.java`、`PaperCrawlerClient.java` |
| 风控 | `risk/PaperRiskGuard.java`、`PaperPositionExitService.java`、`PaperStageService.java`、`PaperRiskSettingsService.java` |
| 票仓 | `warehouse/PaperWarehouseCleanupService.java`、`PaperWarehouseAnalystService.java` |
| 评估 | `evaluator/PaperTradeEvaluatorService.java`、`mail/PaperMailService.java` |
| 定时 | `schedule/PaperScheduleManager.java`、`PaperScheduleService.java` |
| 工作流视图 | `workflow/PaperWorkflowBlueprints.java`、`PaperWorkflowViewService.java` |
| 审计 | `audit/PaperAiToolAuditAspect.java` |
| 前端壳 | `PaperTradeLayout.vue`、`PositionsView.vue`、`HistoryView.vue`、`AccountSettingsView.vue`、`WorkflowView.vue` |
| 历史扩展 | `history/resources.ts`、`api/index.ts`、`types.ts`、`utils/valueTier.ts` |

## 已知边界

- 虚拟盘，不接券商；撮合价取收盘价，不适合盘中高频验证。
- 单机单用户，没有账户隔离与权限；初始资金、风控参数都是全局单行。
- 交易评估员邮件依赖 `PAPER_MAIL_*` / `spring.mail.*`；未配齐只落日志并记 `SKIPPED`。
- 票仓清理是「一次性状态机」：1 会被降回 2，2 到期降 3，3 直接删；没有回收站。
- 工作流视图是只读蓝图 + 最近一轮轨迹，不可在前端拖拽编排。
