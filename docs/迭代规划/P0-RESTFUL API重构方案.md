---
name: HTTP 请求重构
overview: 将 `http.ts` + `request.ts` 双层封装合并为单一 `HttpClient`（在响应拦截器中一次性解包 `Result<T>`），并新增 `src/api/` 领域 API 层；失败时 throw `ApiError`，默认自动 toast，调用方用 try/catch。流式 API 保持 `streamChat.ts` 不动。
todos:
  - id: create-http-module
    content: 新建 utils/http/{types,client,index}.ts，实现 HttpClient + ragClient/crmClient
    status: completed
  - id: create-api-layer
    content: 新建 api/chat.ts、knowledge.ts、workspace.ts
    status: completed
  - id: migrate-consumers
    content: 迁移 4 个调用方（chat store、ChatBox、KnowledgeStore、WorkspaceManagement）
    status: completed
  - id: remove-old-files
    content: 删除旧 http.ts、request.ts，确认无残留 import
    status: completed
  - id: verify-build
    content: 运行 npm run build 并手动冒烟 REST 接口
    status: completed
isProject: false
---

# 网络请求封装重构方案

## 现状问题

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

## 目标架构

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

## 新文件结构

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

## 1. `utils/http/types.ts`

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

## 2. `utils/http/client.ts`（核心）

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

## 3. `src/api/` 领域 API 层

将 URL 字符串与泛型类型集中管理，Store/组件只关心业务语义。

### `api/chat.ts`

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

### `api/knowledge.ts`

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

### `api/workspace.ts`

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

## 4. 调用方迁移（4 个文件）

| 文件 | 改动 |
|------|------|
| [stores/chat.ts](my-chat-vue3/src/stores/chat.ts) | `chatApi.*` 替换 `request/mutate/ragHttp`；修复 `deleteConversation` 未 await 的 bug |
| [components/ChatBox.vue](my-chat-vue3/src/components/ChatBox.vue) | `chatApi.getMessages` |
| [views/knowledgeStore/KnowledgeStore.vue](my-chat-vue3/src/views/knowledgeStore/KnowledgeStore.vue) | `knowledgeApi.*` |
| [views/settings/components/WorkspaceManagement.vue](my-chat-vue3/src/views/settings/components/WorkspaceManagement.vue) | `workspaceApi.*` |

### 迁移示例：`stores/chat.ts`

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

---

## 5. 类型清理（可选）

- [types/models.ts](my-chat-vue3/src/types/models.ts) 中的 `Result` / `ResultData` 可保留（其他模块可能引用），或改为从 `@/utils/http` re-export，避免重复定义
- [types/enums.ts](my-chat-vue3/src/types/enums.ts) 的 `ResultEnum.SUCCESS` / `TIMEOUT` 继续复用

---

## 6. 验证

```bash
cd my-chat-vue3
npm run build   # vue-tsc + vite build，项目唯一的类型检查入口
```

手动冒烟：
- 会话列表 CRUD
- 知识库列表 / 上传 / 删除
- 工作区目录树 / 文件导入 / 预览

---

## 不在本次范围

- 流式聊天 [streamChat.ts](my-chat-vue3/src/utils/streamChat.ts) + [ChatController L36-55](my-chat-server/src/main/java/com/mychat/controller/ChatController.java)
- Token 硬编码抽取到 env（可后续单独做）
- `ResultEnum.EXPIRE`（305/601/602）的统一登出跳转逻辑（当前未实现，可后续在拦截器加）
