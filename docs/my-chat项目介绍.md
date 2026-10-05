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

# @ 其他

## @.1 异步任务 + 消息通知

> **状态**：已落地（验收用 `demo_sleep` 已删除，管道保留）  
> **目标**：长任务不堵 HTTP；用户可离开当前页；完成后全局弹窗 + 音效。  
> **原则**：后端 Job 是事实源，前端只订阅。对标 Dify / FastGPT 的「任务 + 通知铃」，不用 Redis/MQ，也不在 Pinia 里自管队列。

---

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