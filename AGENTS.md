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
```

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
- `/rag/**` → `http://localhost:8100` (project backend)
- `/api/**` → `http://localhost:8080/jeecg-boot` (legacy CRM, keep but don't modify unless asked)

Two Axios instances in `src/utils/http.ts`: `ragHttp` (for this backend) and `crmHttp` (for legacy CRM). There is a hard-coded `X-Access-Token` header — don't remove it.

## API conventions

Backend wraps all responses in `Result<T>` (`{ code, message, data }`). Code 200 = success. Frontend `request.ts` auto-unwraps and error-handles.

For SSE streaming chat, the frontend uses native `fetch` (not Axios) — see `src/utils/streamChat.ts`. The backend chat endpoint (`/ai/normalChat/chat`) produces `text/html` for streaming, not `text/event-stream`.

## Key backend internals

- `AiConfiguration` wires ChatClient with ShellTool + FFmpegTool as default tools, JDBC-backed `MessageWindowChatMemory` (max 64 messages), and `SimpleLoggerAdvisor`.
- `EmbeddingService` writes debug logs to the project root `debug-d859f8.log` via `AgentDebugLog`. This is debug infrastructure — leave it alone unless instructed.
- `EmbeddingConfigProbe` logs resolved config at startup.

## Tests

Tests are `@SpringBootTest` integration tests. They need a running PostgreSQL with pgvector and the required env vars set.

Video-related tests (`FfmpegUtilTest`, `VideoServiceTest`) need `src/test/resources/test-video.mp4` placed manually — they silently skip if the file is missing.

## Version note (README is stale)

The README badges claim Spring Boot 3.5 and Spring AI 1.1.3, but the actual `pom.xml` declares Spring Boot **4.1.0**, Spring AI **2.0.0**, and Java **25**. Trust `pom.xml` over README.
