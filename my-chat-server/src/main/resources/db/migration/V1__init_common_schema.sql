-- My-Chat 基础通用结构（PostgreSQL 14+ / schema public）
-- Flyway V1：仅用于空库初始化；已有库通过 baseline-version=3 接管。

SET search_path TO public;

-- pgvector 必须已安装；扩展可能由 DBA 预先创建，因此保留 IF NOT EXISTS。
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 用户
CREATE TABLE users
(
    id         BIGSERIAL PRIMARY KEY,
    username   VARCHAR(50) UNIQUE NOT NULL,
    email      VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON COLUMN users.id IS '用户唯一ID，自增主键';
COMMENT ON COLUMN users.username IS '用户名，必须唯一且不能为空';
COMMENT ON COLUMN users.email IS '用户邮箱，可选';
COMMENT ON COLUMN users.created_at IS '创建时间，默认当前时间戳';

-- 聊天会话
CREATE TABLE chat_sessions
(
    conversation_id VARCHAR(255) PRIMARY KEY,
    user_id         BIGINT,
    title           VARCHAR(100),
    kb_id           VARCHAR(64),
    work_dir        VARCHAR(500),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE chat_sessions IS '聊天会话总表，用于存储用户与AI的每一次对话会话基本信息';
COMMENT ON COLUMN chat_sessions.conversation_id IS '会话唯一标识符（主键），通常由前端生成UUID或雪花ID';
COMMENT ON COLUMN chat_sessions.user_id IS '发起该会话的用户ID，关联用户主表';
COMMENT ON COLUMN chat_sessions.title IS '会话标题，方便用户历史记录展示';
COMMENT ON COLUMN chat_sessions.kb_id IS '本次会话关联的知识库ID（可为空），用于RAG场景下的知识库隔离';
COMMENT ON COLUMN chat_sessions.work_dir IS '本次会话关联的工作目录';
COMMENT ON COLUMN chat_sessions.created_at IS '会话创建时间（含时分秒）';
COMMENT ON COLUMN chat_sessions.updated_at IS '会话最后更新时间（含时分秒），用于排序活跃会话；由应用层维护';

-- Spring AI 聊天记忆
CREATE TABLE spring_ai_chat_memory
(
    id              BIGSERIAL PRIMARY KEY,
    conversation_id VARCHAR(255) NOT NULL,
    type            VARCHAR(50)  NOT NULL,
    content         TEXT         NOT NULL,
    timestamp       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    sequence_id     BIGINT
);
COMMENT ON TABLE spring_ai_chat_memory IS 'Spring AI框架使用的对话记忆明细表，存储每一轮交互的消息记录';
COMMENT ON COLUMN spring_ai_chat_memory.id IS '记录唯一ID（自增主键），使用BIGSERIAL自动生成';
COMMENT ON COLUMN spring_ai_chat_memory.conversation_id IS '所属会话ID，逻辑外键关联chat_sessions表';
COMMENT ON COLUMN spring_ai_chat_memory.type IS '消息角色类型，例如：user、assistant、system';
COMMENT ON COLUMN spring_ai_chat_memory.content IS '消息正文内容，存储纯文本或Markdown格式的对话内容';
COMMENT ON COLUMN spring_ai_chat_memory.timestamp IS '该条消息产生的时间戳，默认自动填充当前时间';
COMMENT ON COLUMN spring_ai_chat_memory.sequence_id IS '消息在会话内的顺序编号，用于按序还原完整对话上下文';

CREATE INDEX idx_chat_sessions_user_id ON chat_sessions (user_id);
CREATE INDEX idx_conversation_id ON spring_ai_chat_memory (conversation_id);
CREATE INDEX idx_memory_sequence_conv ON spring_ai_chat_memory (sequence_id, conversation_id);

-- 向量存储（embedding 维度须与配置中的模型一致）
CREATE TABLE vector_store
(
    id        UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
    content   TEXT,
    metadata  JSONB,
    embedding vector(1536)
);
COMMENT ON TABLE vector_store IS '向量存储主表';
COMMENT ON COLUMN vector_store.id IS '主键，UUID自动生成';
COMMENT ON COLUMN vector_store.content IS '待检索文本：有摘要时为「【摘要】…【原文】…」，否则为切段原文';
COMMENT ON COLUMN vector_store.metadata IS 'JSONB：kbId、documentId、filename 必填；可选 summary、original';
COMMENT ON COLUMN vector_store.embedding IS '1536维向量嵌入值';

CREATE INDEX idx_vector_store_embedding
    ON vector_store
    USING hnsw (embedding vector_cosine_ops);

-- 知识库
CREATE TABLE knowledge_base
(
    id                   VARCHAR(64) PRIMARY KEY,
    name                 VARCHAR(200) NOT NULL,
    description          TEXT,
    chunk_size           INT              DEFAULT 800 NOT NULL,
    chunk_overlap        INT              DEFAULT 0   NOT NULL,
    top_k                INT              DEFAULT 5   NOT NULL,
    similarity_threshold DOUBLE PRECISION DEFAULT 0.5 NOT NULL,
    created_at           TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE knowledge_base IS '知识库基础信息';
COMMENT ON COLUMN knowledge_base.id IS '知识库唯一ID';
COMMENT ON COLUMN knowledge_base.name IS '知识库名称';
COMMENT ON COLUMN knowledge_base.description IS '知识库描述';
COMMENT ON COLUMN knowledge_base.chunk_size IS '入库切分目标 token 数';
COMMENT ON COLUMN knowledge_base.chunk_overlap IS '相邻分片重叠 token 数，须小于 chunk_size';
COMMENT ON COLUMN knowledge_base.top_k IS '检索返回片段上限';
COMMENT ON COLUMN knowledge_base.similarity_threshold IS '检索相似度下限 0~1';
COMMENT ON COLUMN knowledge_base.created_at IS '创建时间';
COMMENT ON COLUMN knowledge_base.updated_at IS '更新时间（应用层维护）';

-- 文档元数据
CREATE TABLE document_meta
(
    id            VARCHAR(64) PRIMARY KEY,
    kb_id         VARCHAR(64)  NOT NULL REFERENCES knowledge_base (id) ON DELETE CASCADE,
    filename      VARCHAR(500) NOT NULL,
    file_size     BIGINT,
    file_type     VARCHAR(50),
    chunk_count   INT         DEFAULT 0,
    status        VARCHAR(20) DEFAULT 'PROCESSING',
    storage_path  VARCHAR(1000),
    error_message TEXT,
    created_at    TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP   DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE document_meta IS '文档元数据及处理状态';
COMMENT ON COLUMN document_meta.id IS '文档唯一ID';
COMMENT ON COLUMN document_meta.kb_id IS '所属知识库ID（外键，级联删除）';
COMMENT ON COLUMN document_meta.filename IS '原始文件名';
COMMENT ON COLUMN document_meta.file_size IS '文件大小（单位：字节）';
COMMENT ON COLUMN document_meta.file_type IS '文件类型（如 PDF/TXT/DOCX）';
COMMENT ON COLUMN document_meta.chunk_count IS '该文档的切片总数';
COMMENT ON COLUMN document_meta.status IS '处理状态（PROCESSING / READY / FAILED）';
COMMENT ON COLUMN document_meta.storage_path IS '原始文件落盘路径，删文档时一并删';
COMMENT ON COLUMN document_meta.error_message IS '入库失败原因（用户可见）';
COMMENT ON COLUMN document_meta.created_at IS '创建时间';
COMMENT ON COLUMN document_meta.updated_at IS '更新时间（应用层维护）';

CREATE INDEX idx_document_meta_kb_id ON document_meta (kb_id);

-- 文档切段原文与摘要
CREATE TABLE document_chunk
(
    id          VARCHAR(64) PRIMARY KEY,
    document_id VARCHAR(64) NOT NULL REFERENCES document_meta (id) ON DELETE CASCADE,
    kb_id       VARCHAR(64) NOT NULL,
    position    INT         NOT NULL,
    content     TEXT        NOT NULL,
    summary     TEXT,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (document_id, position)
);
COMMENT ON TABLE document_chunk IS '文档切段原文与摘要，只读展示用，与 vector_store 入库双写';
COMMENT ON COLUMN document_chunk.id IS '与向量段同一套 nameUUID(documentId_下标)';
COMMENT ON COLUMN document_chunk.document_id IS '所属文档（外键，级联删除）';
COMMENT ON COLUMN document_chunk.kb_id IS '所属知识库，列表过滤冗余';
COMMENT ON COLUMN document_chunk.position IS '切段下标，从 0 起';
COMMENT ON COLUMN document_chunk.content IS '切段原文（metadata.original，无则 Document 文本）';
COMMENT ON COLUMN document_chunk.summary IS 'chunk 摘要，失败降级可空';
COMMENT ON COLUMN document_chunk.created_at IS '写入时间';

CREATE INDEX idx_document_chunk_document_id ON document_chunk (document_id);

-- 助手回合 UI 轨迹
CREATE TABLE chat_assistant_turns
(
    id                BIGSERIAL PRIMARY KEY,
    conversation_id   VARCHAR(255) NOT NULL,
    turn_id           VARCHAR(128) NOT NULL,
    assistant_ordinal INT          NOT NULL,
    assistant_text    TEXT,
    thinking          TEXT,
    parts             JSONB        NOT NULL DEFAULT '[]'::jsonb,
    created_at        TIMESTAMP             DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_assistant_turn UNIQUE (conversation_id, turn_id),
    CONSTRAINT uq_assistant_ordinal UNIQUE (conversation_id, assistant_ordinal)
);
CREATE INDEX idx_assistant_turns_conv
    ON chat_assistant_turns (conversation_id, assistant_ordinal);
COMMENT ON TABLE chat_assistant_turns IS '每轮 ASSISTANT 的 UI parts/thinking，用于刷新后时间线回放';
COMMENT ON COLUMN chat_assistant_turns.conversation_id IS '逻辑外键，对应 chat_sessions.conversation_id';
COMMENT ON COLUMN chat_assistant_turns.turn_id IS '与流式 NDJSON 的 turnId 一致（chatId-UUID）';
COMMENT ON COLUMN chat_assistant_turns.assistant_ordinal IS '该会话内 ASSISTANT 消息序号（0-based），用于与 Memory 对齐';
COMMENT ON COLUMN chat_assistant_turns.parts IS '归约后的 MessagePart[] JSON，与前端 ToolMessagePart 同构';

-- 长对话滚动摘要
CREATE TABLE chat_session_summary
(
    conversation_id           VARCHAR(255) PRIMARY KEY,
    summary_text              TEXT        NOT NULL,
    covered_until_sequence_id BIGINT      NOT NULL DEFAULT 0,
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE chat_session_summary IS 'Orchestrate 会话级滚动摘要：压缩较早轮次，配合近期 Memory 原文注入';
COMMENT ON COLUMN chat_session_summary.conversation_id IS '会话 ID，对应 chat_sessions.conversation_id / Memory conversation_id';
COMMENT ON COLUMN chat_session_summary.summary_text IS '较早轮次压缩后的中文摘要';
COMMENT ON COLUMN chat_session_summary.covered_until_sequence_id IS '摘要已覆盖到的 spring_ai_chat_memory.sequence_id 上界';
COMMENT ON COLUMN chat_session_summary.updated_at IS '摘要最后更新时间';

-- 通用异步任务
CREATE TABLE async_job
(
    id                VARCHAR(64) PRIMARY KEY,
    job_type          VARCHAR(64)  NOT NULL,
    status            VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    title             VARCHAR(200) NOT NULL,
    ref_id            VARCHAR(64),
    payload           TEXT,
    error_message     TEXT,
    notify_on_success BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    finished_at       TIMESTAMP
);
CREATE INDEX idx_async_job_status ON async_job (status);
CREATE INDEX idx_async_job_created ON async_job (created_at DESC);
COMMENT ON TABLE async_job IS '通用后台任务：PENDING→RUNNING→SUCCEEDED/FAILED；前端经 SSE 订阅终态';
COMMENT ON COLUMN async_job.job_type IS '任务类型，由业务 Handler 注册，如 kb_ingest';
COMMENT ON COLUMN async_job.status IS 'PENDING / RUNNING / SUCCEEDED / FAILED';
COMMENT ON COLUMN async_job.title IS '通知标题（用户可见）';
COMMENT ON COLUMN async_job.ref_id IS '业务主键，如 document_meta.id；演示任务可空';
COMMENT ON COLUMN async_job.payload IS 'JSON 字符串，handler 入参';
COMMENT ON COLUMN async_job.error_message IS '失败原因（截断）';
COMMENT ON COLUMN async_job.notify_on_success IS '成功是否弹窗+音效；失败始终提醒';
COMMENT ON COLUMN async_job.finished_at IS '进入终态的时间';

-- 大模型目录
CREATE TABLE llm_model
(
    id         VARCHAR(64) PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    provider   VARCHAR(50)  NOT NULL,
    base_url   VARCHAR(500) NOT NULL,
    api_key    VARCHAR(500) NOT NULL,
    model_id   VARCHAR(200) NOT NULL,
    max_tokens INT          NOT NULL DEFAULT 8192,
    enabled    BOOLEAN      NOT NULL DEFAULT TRUE,
    is_default BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP             DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP             DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_llm_model_default ON llm_model (is_default);
COMMENT ON TABLE llm_model IS 'OpenAI 兼容对话模型目录：设置页维护，运行时按 is_default 切换 ChatClient';
COMMENT ON COLUMN llm_model.id IS '模型配置唯一ID（UUID）';
COMMENT ON COLUMN llm_model.name IS '展示名称，如 DeepSeek Flash';
COMMENT ON COLUMN llm_model.provider IS '供应商预设键：deepseek / openai / qwen / ollama / siliconflow / custom';
COMMENT ON COLUMN llm_model.base_url IS 'OpenAI 兼容 API 根地址';
COMMENT ON COLUMN llm_model.api_key IS '调用密钥（接口回传时脱敏）';
COMMENT ON COLUMN llm_model.model_id IS '供应商侧模型名，如 deepseek-v4-flash';
COMMENT ON COLUMN llm_model.max_tokens IS '单次生成 token 上限';
COMMENT ON COLUMN llm_model.enabled IS '是否启用；禁用后不可设为默认';
COMMENT ON COLUMN llm_model.is_default IS '是否为当前全局默认（应用层保证至多一行 true）';
COMMENT ON COLUMN llm_model.created_at IS '创建时间';
COMMENT ON COLUMN llm_model.updated_at IS '更新时间，由应用层维护';
