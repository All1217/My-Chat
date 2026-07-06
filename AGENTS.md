# AGENTS.md

## Architecture

Two-package monorepo. Root `src/` is a vestige — ignore it.

- **`my-chat-server/`** — Java / Spring Boot 4.1.0 / Spring AI 2.0.0 backend (port **8100**)
- **`my-chat-vue3/`** — Vue 3 + Vite 6 + TypeScript + Pinia + Element Plus frontend (dev port **5173**)

## Dev commands

```bash
# Backend (from my-chat-server/)
./mvnw.cmd spring-boot:run        # start dev server
./mvnw.cmd test                    # run all tests
./mvnw.cmd test -Dtest=ClassName   # run single test class

# Frontend (from my-chat-vue3/)
npm run dev                        # start dev server
npm run build                      # typecheck + build (vue-tsc -b && vite build)
npm run preview                    # preview production build
```

**Frontend has no lint or test framework.** `npm run build` is the only verification step. No ESLint, Prettier, or Vitest is configured.

**Frontend path alias:** `@/` maps to `src/` (configured in `vite.config.ts`).

## Required environment variables

| Variable | Used for |
|---|---|
| `OPENAI_API_KEY` | DeepSeek chat model |
| `EMBEDDING_MODEL_API_KEY` | Alibaba Cloud MaaS embedding |
| `PGSQL_PASS` | PostgreSQL password |

## Database

Uses **PostgreSQL** (not MySQL — README badges are stale). Requires the **pgvector** extension.

Schema is in `my-chat-server/src/main/resources/schema.sql`. The file contains both MySQL and PostgreSQL DDL; the PostgreSQL block is at the bottom. Spring AI auto-creates vector store tables at startup.

## Frontend proxy

Vite dev server proxies:
- `/rag/*` → `http://localhost:8100` (project backend, path prefix stripped)
- `/api/*` → `http://localhost:8080/jeecg-boot` (legacy CRM — keep but don't modify unless asked)

Two Axios instances in `my-chat-vue3/src/utils/http.ts`: `ragHttp` (base `/rag`) and `crmHttp` (base `/api`). A hard-coded `X-Access-Token` header is injected in both — do not remove it.

## API conventions

Backend wraps all responses in `Result<T>` (`{ code, message, data }`). Code 200 = success. Frontend `my-chat-vue3/src/utils/request.ts` auto-unwraps and error-handles.

For streaming chat, the frontend uses native `fetch` (not Axios) — see `my-chat-vue3/src/utils/streamChat.ts`. The backend chat endpoint `/ai/normalChat/chat` produces `text/html;charset=utf-8` (not `text/event-stream`). The stream may contain `[THINKING]...[/THINKING]` tags wrapping reasoning content — the frontend Markdown renderer must handle these.

## Backend API surface

Three controllers at `/ai/*`:
- `ChatController` (`/ai/normalChat/chat`) — streaming POST, `text/html;charset=utf-8`, uses FormData (prompt + chatId + optional files)
- `ChatHistoryController` (`/ai/history/*`) — session CRUD (getConversations, addConversation, update, deleteById, getMessages)
- `FileController` (`/ai/file/*`) — workspace management (tree, list, read, CRUD) and document upload/vectorize

## Key backend internals

- `AiConfiguration` wires ChatClient with ShellTool as the default tool, JDBC-backed `MessageWindowChatMemory` (max 64 messages), and `SimpleLoggerAdvisor`.
- MyBatis-Plus 3.5.15 (uses `spring-boot4-starter` matching SB 4.x).
- File upload: 200MB max (multipart). Read timeout: 600s.
- Chat model: `deepseek-v4-pro` (OpenAI-compatible API at `api.deepseek.com`), thinking disabled.
- Embedding model: Alibaba MaaS `text-embedding-v4`, 1536d, pgvector with HNSW + cosine distance.
- `ShellTool` is a **Windows-only** read-only PowerShell command executor with a whitelist. It runs in the `my-chat-server/` CWD and has a 15s timeout. Will not work on Linux/macOS.
- Embedded FFmpeg bundled at `src/main/resources/ffmpeg/windows/ffmpeg.exe` (enabled via `app.ffmpeg.use-embedded: true`). Falls back to system PATH ffmpeg if missing.
- Workspace root: `./src/main/resources/workspace` (configurable via `app.workspace.root`).
- `EmbeddingService` writes debug logs to project root `debug-d859f8.log` via `AgentDebugLog`. Leave it alone unless instructed.
- `EmbeddingConfigProbe` logs resolved config at startup.
- Knowledge base CRUD: `entity/po/KnowledgeBase.java`, `entity/po/DocumentMeta.java`, `KnowledgeBaseController` at `/ai/knowledge-base/*`.
- Document upload accepts optional `kbId` param to associate with a knowledge base; document metadata goes into `document_meta` table. Vector cleanup uses `EmbeddingService.deleteByDocumentId`.

## Tests

Tests are `@SpringBootTest` integration tests. They need a running PostgreSQL with pgvector and the required env vars set. Test config (`my-chat-server/src/test/resources/application-test.yaml`) overrides OpenAI to `localhost:9999` with dummy credentials — no real API calls during tests.

## Version note (README is stale)

The README badges claim Spring Boot 3.5 and Spring AI 1.1.3, but `pom.xml` declares Spring Boot **4.1.0**, Spring AI **2.0.0**, and Java **25**. Trust `pom.xml` over README.
