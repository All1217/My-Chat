# AGENTS.md

## Architecture

- **`my-chat-server/`** — Java / Spring Boot 4.1.0 / Spring AI 2.0.0 backend (port **8100**)
- **`my-chat-vue3/`** — Vue 3 + Vite 6 + TypeScript + Pinia + Element Plus frontend (dev port **5173**)
- `docs/` — Chinese-language iteration planning & issue logs (not reliable for current state)

## Dev commands

```bash
# Backend (from my-chat-server/)
./mvnw.cmd spring-boot:run        # start dev server on port 8100
./mvnw.cmd test                    # runs nothing — no test source files exist
./mvnw.cmd test -Dtest=ClassName   # (no src/test/java/ directory exists)

# Frontend (from my-chat-vue3/)
npm run dev                        # start dev server on port 5173
npm run build                      # typecheck + build (vue-tsc -b && vite build)
npm run preview                    # preview production build
```

**Frontend has no lint or test framework.** `npm run build` is the only verification step.

**Frontend path alias:** `@/` maps to `src/`.

## Required environment variables

| Variable | Used for |
|---|---|
| `OPENAI_API_KEY` | DeepSeek chat model |
| `EMBEDDING_MODEL_API_KEY` | Alibaba Cloud MaaS embedding |
| `PGSQL_PASS` | PostgreSQL password |

## Database

PostgreSQL with **pgvector** extension. Schema: `my-chat-server/src/main/resources/schema.sql` (PostgreSQL block at bottom). Spring AI auto-creates `vector_store` table.

## Frontend proxy & clients

Vite proxies:
- `/rag/*` → `http://localhost:8100` (project backend, path prefix **stripped**)
- `/api/*` → `http://localhost:8080/jeecg-boot` (legacy CRM — don't modify)

Two Axios clients in `my-chat-vue3/src/utils/http/` export `ragClient` (base `/rag`) and `crmClient` (base `/api`). A hard-coded `X-Access-Token` is injected in both interceptors — do not remove.

Streaming chat uses native `fetch` (not Axios) — see `streamChat.ts`. The endpoint produces `text/html;charset=utf-8`. Frontend parses `[THINKING_START]...[/THINKING_END]` tags (see `ChatBox.vue:257`).

## API conventions

Backend wraps responses in `Result<T>` (`{ code, message, data }`). Code 200 = success. `HttpClient` (`src/utils/http/client.ts`) auto-unwraps `data` and error-handles via Element Plus `ElMessage`.

## Backend API surface (five controllers at `/ai/*`)

All endpoints are under `com.mychat.controller`.

- **`ChatController`** (`/ai/normalChat/chat`) — streaming POST, FormData (prompt + chatId + optional files). Uses `toolChatClient` which has `FileTools` and chat memory.
- **`ChatHistoryController`** (`/ai/history/*`) — session CRUD. `addConversation` accepts optional `kbId` and `workDir`. `update` uses `@RequestBody ChatSessionsDTO`.
- **`FileController`** (`/ai/file/*`) — workspace tree/lazy-tree/list/read/read-binary, document upload (optional `kbId`)/delete, create-folder, delete, rename, switch-root, import, **roots** (list filesystem drives), **browse** (list subdirectories of an absolute path).
- **`KnowledgeBaseController`** (`/ai/knowledge-base/*`) — list, create, delete KB + list documents by `kbId`.
- **`RagChatController`** (`/ai/ragChat/chat`) — RAG streaming POST, same shape + required `kbId`. Uses `QuestionAnswerAdvisor(filterExpression="kbId == '<id>'")`, topK=5, similarityThreshold=0.5.

## Key backend internals

- **`AiConfiguration`** wires two `ChatClient` beans:
  - `toolChatClient` — normal chat, default tool = `FileTools` (not ShellTool — that's deprecated), `MessageWindowChatMemory` (max 64 messages), `SimpleLoggerAdvisor`
  - `ragChatClient` — RAG chat, no tools, system prompt forbids tool use
- **`FileTools`** — pure **Java NIO** file tool (NOT a shell executor). Each op is a separate `@Tool` method with typed JSON params: ls, tree, cat, grep, stat, write, mkdir, rm, mv, cp. `ShellTool` (single `executeCommand` string parser) is **deprecated** in favor of `FileTools`.
- ThreadLocal cross-thread propagation: `ChatController` runs on Tomcat virtual threads; `FileTools` (called by AI model via reactive streaming) runs on Netty threads. `WorkspaceContext` uses Micrometer `ContextPropagation` + `Hooks.enableAutomaticContextPropagation()`. See `WorkspaceContext` (inner `ThreadLocalAccessor`) and `ContextPropagationConfiguration`.
- MyBatis-Plus 3.5.15 (`spring-boot4-starter`).
- Chat model: `deepseek-v4-flash` (OpenAI-compatible at `api.deepseek.com`), thinking extra-body disabled. Controller reads `reasoningContent` from metadata (reserved for future use).
- Embedding: Alibaba MaaS `text-embedding-v4`, 1536d. Vector store: pgvector HNSW + cosine distance.
- File upload: 200MB max. Read timeout: 600s. Virtual threads enabled. CORS: all origins.
- Workspace root: `./src/main/resources/workspace` (configurable via `app.workspace.root`).
- Document processing: PDFBox (PDF), Apache POI (docx/xlsx). `application.yaml` is the source of truth (not `.yml`).

## Frontend structure

- **Pinia store**: `useChatStore` (`my-chat-vue3/src/stores/chat.ts`) — manages chat list, current chat, KB context, sidebar state.
- **Routes** (6 top-level, 4 settings children):
  `/` (Home), `/about`, `/chat`, `/lobby`, `/store` (KnowledgeStore), `/settings` (→ `model`, `workspace`, `prompt`, `role`).
- **API modules**: `src/api/chat.ts`, `knowledge.ts`, `workspace.ts`.
- **CSS preprocessor**: Less.
- **Settings pages**: model management, workspace management, prompt management, role management.

## Tests

No test source files exist (`src/test/java/` does not exist). `src/test/resources/application-test.yaml` exists but is unused.

## Version note

README badges may be stale. `pom.xml` is the source of truth: Spring Boot **4.1.0**, Spring AI **2.0.0**, Java **25**.
