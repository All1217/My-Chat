# AGENTS.md

## Architecture

Root `src/` is a vestige — ignore it.

- **`my-chat-server/`** — Java / Spring Boot 4.1.0 / Spring AI 2.0.0 backend (port **8100**)
- **`my-chat-vue3/`** — Vue 3 + Vite 6 + TypeScript + Pinia + Element Plus frontend (dev port **5173**)

## Dev commands

```bash
# Backend (from my-chat-server/)
./mvnw.cmd spring-boot:run        # start dev server on port 8100
./mvnw.cmd test                    # run all integration tests
./mvnw.cmd test -Dtest=ClassName   # run single test class

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

PostgreSQL with **pgvector** extension. Schema: `my-chat-server/src/main/resources/schema.sql` (PostgreSQL block at the bottom). Spring AI auto-creates `vector_store` table.

## Frontend proxy

Vite proxies:
- `/rag/*` → `http://localhost:8100` (project backend, path prefix **stripped**)
- `/api/*` → `http://localhost:8080/jeecg-boot` (legacy CRM — don't modify)

Two Axios clients in `my-chat-vue3/src/utils/http/` export `ragClient` (base `/rag`) and `crmClient` (base `/api`). A hard-coded `X-Access-Token` is injected in both interceptors — do not remove.

## API conventions

Backend wraps responses in `Result<T>` (`{ code, message, data }`). Code 200 = success. Frontend `HttpClient` (built into `client.ts`) auto-unwraps and error-handles.

Streaming chat uses native `fetch` (not Axios) — see `streamChat.ts`. Endpoint produces `text/html;charset=utf-8`. The stream may embed `[THINKING]...[/THINKING]` tags; the Markdown renderer must handle them.

## Backend API surface (five controllers at `/ai/*`)

- `ChatController` (`/ai/normalChat/chat`) — streaming POST, uses FormData (prompt + chatId + optional files). Uses `toolChatClient` which has ShellTool and chat memory.
- `ChatHistoryController` (`/ai/history/*`) — session CRUD: getConversations, addConversation, update, deleteById, getMessages. Both `getConversations` and `addConversation` accept optional `kbId`. update uses `@RequestBody ChatSessionsDTO`.
- `FileController` (`/ai/file/*`) — workspace management (tree, lazy tree, list, read, read/binary) + document upload (with optional kbId) / delete. Also: create folder, delete, rename, switch workspace root, import files.
- `KnowledgeBaseController` (`/ai/knowledge-base/*`) — KB CRUD (list, create, delete) + list documents by kbId.
- `RagChatController` (`/ai/ragChat/chat`) — RAG streaming POST, same shape as normalChat + required `kbId`. Uses `QuestionAnswerAdvisor` with `filterExpression("kbId == '<id>'")`, topK=5, similarityThreshold=0.5.

## Key backend internals

- **`AiConfiguration`** wires two `ChatClient` beans:
  - `toolChatClient` — normal chat, has `ShellTool` as default tool, `MessageWindowChatMemory` (max 64 messages), `SimpleLoggerAdvisor`
  - `ragChatClient` — RAG chat, no tools, system prompt forbids tool use
- **`ShellTool`** — pure **Java NIO** file tool, NOT a PowerShell executor. Runs in the workspace root. Commands: ls, tree, cat, grep, stat, write, mkdir, rm, mv, cp. File ops are read+write (no shell fork). Cross-platform (Java only).
- MyBatis-Plus 3.5.15 (uses `spring-boot4-starter`).
- File upload: 200MB max. Read timeout: 600s.
- Chat model: `deepseek-v4-flash` (OpenAI-compatible at `api.deepseek.com`), thinking extra-body `disabled`. The controller code reads `reasoningContent` from metadata for future use when thinking is enabled.
- Embedding: Alibaba MaaS `text-embedding-v4`, 1536d, pgvector HNSW + cosine distance.
- Virtual threads enabled (`spring.threads.virtual.enabled: true`).
- CORS: all origins allowed (`MvcConfiguration`).
- Workspace root: `./src/main/resources/workspace` (configurable via `app.workspace.root`).
- Document processing: PDFBox (PDF), Apache POI (docx/xlsx).
- Session table `chat_sessions` has `kb_id` column for KB-scoped sessions.

## Tests

`@SpringBootTest` integration tests. Need running PostgreSQL + pgvector + env vars. Test config (`src/test/resources/application-test.yaml`) overrides OpenAI model to `localhost:9999` with dummy creds — no real API calls.

## Version note

README badges may be stale. `pom.xml` is the source of truth: Spring Boot **4.1.0**, Spring AI **2.0.0**, Java **25**.
