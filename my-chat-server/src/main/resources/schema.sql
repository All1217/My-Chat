-- PGSQL版
-- 1. 用户表
CREATE TABLE IF NOT EXISTS users
(
    id BIGSERIAL PRIMARY KEY, -- AUTO_INCREMENT 改为 BIGSERIAL
    username   VARCHAR(50) UNIQUE NOT NULL,
    email      VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
-- 2. 使用 COMMENT ON 给每个字段加解释（注释）
COMMENT ON COLUMN users.id IS '用户唯一ID，自增主键';
COMMENT ON COLUMN users.username IS '用户名，必须唯一且不能为空';
COMMENT ON COLUMN users.email IS '用户邮箱，可选';
COMMENT ON COLUMN users.created_at IS '创建时间，默认当前时间戳';

-- 2. 聊天会话表
CREATE TABLE IF NOT EXISTS chat_sessions
(
    conversation_id VARCHAR(255) PRIMARY KEY,
    user_id         BIGINT,
    title           VARCHAR(100),
    kb_id           VARCHAR(64) NULL,
    work_dir        VARCHAR(500) DEFAULT NULL,
    created_at      TIMESTAMP    DEFAULT CURRENT_DATE,     -- DATETIME 改为 TIMESTAMP
    updated_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP -- 去掉了 ON UPDATE，PostgreSQL 不支持该写法
);
COMMENT ON TABLE chat_sessions IS '聊天会话总表，用于存储用户与AI的每一次对话会话基本信息';
COMMENT ON COLUMN chat_sessions.conversation_id IS '会话唯一标识符（主键），通常由前端生成UUID或雪花ID';
COMMENT ON COLUMN chat_sessions.user_id IS '发起该会话的用户ID，关联用户主表';
COMMENT ON COLUMN chat_sessions.title IS '会话标题，方便用户历史记录展示';
COMMENT ON COLUMN chat_sessions.kb_id IS '本次会话关联的知识库ID（可为空），用于RAG场景下的知识库隔离';
COMMENT ON COLUMN chat_sessions.work_dir IS '本次会话关联的工作目录';
COMMENT ON COLUMN chat_sessions.created_at IS '会话创建日期（注意：默认值为CURRENT_DATE，粒度只到天，不含具体时分秒）';
COMMENT ON COLUMN chat_sessions.updated_at IS '会话最后更新时间（含时分秒），用于排序活跃会话；因PGSQL无ON UPDATE自动更新，需应用层手动维护';

-- 3. Spring AI 聊天记忆表
CREATE TABLE IF NOT EXISTS spring_ai_chat_memory
(
    id BIGSERIAL PRIMARY KEY, -- AUTO_INCREMENT 改为 BIGSERIAL
    conversation_id VARCHAR(255) NOT NULL,
    type            VARCHAR(50)  NOT NULL,
    content         TEXT         NOT NULL,
    timestamp       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    sequence_id     BIGINT
);
COMMENT ON TABLE spring_ai_chat_memory IS 'Spring AI框架使用的对话记忆明细表，存储每一轮交互的消息记录';
COMMENT ON COLUMN spring_ai_chat_memory.id IS '记录唯一ID（自增主键），使用BIGSERIAL自动生成';
COMMENT ON COLUMN spring_ai_chat_memory.conversation_id IS '所属会话ID，逻辑外键关联chat_sessions表';
COMMENT ON COLUMN spring_ai_chat_memory.type IS '消息角色类型，例如：user（用户）、assistant（AI助手）、system（系统提示）';
COMMENT ON COLUMN spring_ai_chat_memory.content IS '消息正文内容，存储纯文本或Markdown格式的对话内容';
COMMENT ON COLUMN spring_ai_chat_memory.timestamp IS '该条消息产生的时间戳，默认自动填充当前时间';
COMMENT ON COLUMN spring_ai_chat_memory.sequence_id IS '消息在会话内的顺序编号，用于按序还原完整对话上下文（数字越大代表越靠后）';

-- 4. 索引（去掉了反引号，缩短了最后一个索引名以防超长）
CREATE INDEX IF NOT EXISTS idx_users_username ON users (username);
CREATE INDEX IF NOT EXISTS idx_chat_sessions_user_id ON chat_sessions (user_id);
CREATE INDEX IF NOT EXISTS idx_conversation_id ON spring_ai_chat_memory (conversation_id);
CREATE INDEX IF NOT EXISTS idx_memory_sequence_conv ON spring_ai_chat_memory (sequence_id, conversation_id);

-- 向量存储表初始化
CREATE
EXTENSION IF NOT EXISTS vector;
CREATE
EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS public.vector_store
(
    id uuid DEFAULT uuid_generate_v4() PRIMARY KEY,
    content text,
    -- metadata 对应 Document 类的 metadata 属性，Document --> org.springframework.ai.document;
    metadata jsonb,
    embedding vector(1536)
);
COMMENT ON TABLE vector_store IS '向量存储主表';
COMMENT ON COLUMN vector_store.id IS '主键，UUID自动生成';
COMMENT ON COLUMN vector_store.content IS '待检索文本：有摘要时为「【摘要】…【原文】…」，否则为切段原文';
COMMENT ON COLUMN vector_store.metadata IS 'JSONB：kbId、documentId、filename 必填；可选 summary（chunk 摘要）、original（未拼接的原文）';
COMMENT ON COLUMN vector_store.embedding IS '1536维向量嵌入值';

CREATE INDEX IF NOT EXISTS idx_vector_store_embedding
    ON public.vector_store
    USING hnsw (embedding vector_cosine_ops);

-- knowledge_base（知识库）
CREATE TABLE IF NOT EXISTS knowledge_base
(
    id                     VARCHAR(64) PRIMARY KEY,
    name                   VARCHAR(200) NOT NULL,
    description            TEXT,
    chunk_size             INT                   DEFAULT 800  NOT NULL,
    chunk_overlap          INT                   DEFAULT 0    NOT NULL,
    top_k                  INT                   DEFAULT 5    NOT NULL,
    similarity_threshold   DOUBLE PRECISION      DEFAULT 0.5  NOT NULL,
    created_at             TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMP DEFAULT CURRENT_TIMESTAMP
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

-- 已有库 CREATE IF NOT EXISTS 不会加列
ALTER TABLE knowledge_base ADD COLUMN IF NOT EXISTS chunk_size INT NOT NULL DEFAULT 800;
ALTER TABLE knowledge_base ADD COLUMN IF NOT EXISTS chunk_overlap INT NOT NULL DEFAULT 0;
ALTER TABLE knowledge_base ADD COLUMN IF NOT EXISTS top_k INT NOT NULL DEFAULT 5;
ALTER TABLE knowledge_base ADD COLUMN IF NOT EXISTS similarity_threshold DOUBLE PRECISION NOT NULL DEFAULT 0.5;

-- document_meta（文档元数据）
CREATE TABLE IF NOT EXISTS document_meta
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

-- 已有库 CREATE IF NOT EXISTS 不会加列，启动脚本补齐
ALTER TABLE document_meta ADD COLUMN IF NOT EXISTS storage_path VARCHAR(1000);
ALTER TABLE document_meta ADD COLUMN IF NOT EXISTS error_message TEXT;

CREATE INDEX IF NOT EXISTS idx_document_meta_kb_id ON document_meta (kb_id);

-- document_chunk（切段原文+摘要，只读展示；与 vector_store 入库双写）
CREATE TABLE IF NOT EXISTS document_chunk
(
    id          VARCHAR(64) PRIMARY KEY,
    document_id VARCHAR(64) NOT NULL REFERENCES document_meta (id) ON DELETE CASCADE,
    kb_id       VARCHAR(64) NOT NULL,
    position    INT         NOT NULL,
    content     TEXT        NOT NULL,
    summary     TEXT,
    created_at  TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (document_id, position)
);
COMMENT ON TABLE document_chunk IS '文档切段原文与摘要，只读展示用，与 vector_store 入库双写；已有 READY 文档需重新向量化才有行';
COMMENT ON COLUMN document_chunk.id IS '与向量段同一套 nameUUID(documentId_下标)';
COMMENT ON COLUMN document_chunk.document_id IS '所属文档（外键，级联删除）';
COMMENT ON COLUMN document_chunk.kb_id IS '所属知识库，列表过滤冗余';
COMMENT ON COLUMN document_chunk.position IS '切段下标，从 0 起';
COMMENT ON COLUMN document_chunk.content IS '切段原文（metadata.original，无则 Document 文本）';
COMMENT ON COLUMN document_chunk.summary IS 'chunk 摘要，失败降级可空';
COMMENT ON COLUMN document_chunk.created_at IS '写入时间';

CREATE INDEX IF NOT EXISTS idx_document_chunk_document_id ON document_chunk (document_id);

-- 助手回合 UI 轨迹（进阶 3：工具时间线回放）
-- 逻辑外键 conversation_id → chat_sessions.conversation_id
-- 不修改 spring_ai_chat_memory
CREATE TABLE IF NOT EXISTS chat_assistant_turns
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

CREATE INDEX IF NOT EXISTS idx_assistant_turns_conv
    ON chat_assistant_turns (conversation_id, assistant_ordinal);

COMMENT ON TABLE chat_assistant_turns IS '每轮 ASSISTANT 的 UI parts/thinking，用于刷新后时间线回放';
COMMENT ON COLUMN chat_assistant_turns.conversation_id IS '逻辑外键，对应 chat_sessions.conversation_id';
COMMENT ON COLUMN chat_assistant_turns.turn_id IS '与流式 NDJSON 的 turnId 一致（chatId-UUID）';
COMMENT ON COLUMN chat_assistant_turns.assistant_ordinal IS '该会话内 ASSISTANT 消息序号（0-based），用于与 Memory 对齐';
COMMENT ON COLUMN chat_assistant_turns.parts IS '归约后的 MessagePart[] JSON，与前端 ToolMessagePart 同构。type=step 且 name=retrieve_kb 时，args.citations 为来源列表（filename / documentId / score / kind / 可选短摘录），args.kbScope 为 catalog|vector';

-- 编排读路径：长对话滚动摘要（与 spring_ai_chat_memory 配合；不替代 Memory 明细）
CREATE TABLE IF NOT EXISTS chat_session_summary
(
    conversation_id            VARCHAR(255) PRIMARY KEY,
    summary_text               TEXT         NOT NULL,
    covered_until_sequence_id  BIGINT       NOT NULL DEFAULT 0,
    updated_at                 TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE chat_session_summary IS 'Orchestrate 会话级滚动摘要：压缩较早轮次，配合近期 Memory 原文注入';
COMMENT ON COLUMN chat_session_summary.conversation_id IS '会话 ID，对应 chat_sessions.conversation_id / Memory conversation_id';
COMMENT ON COLUMN chat_session_summary.summary_text IS '较早轮次压缩后的中文摘要';
COMMENT ON COLUMN chat_session_summary.covered_until_sequence_id IS '摘要已覆盖到的 spring_ai_chat_memory.sequence_id 上界';
COMMENT ON COLUMN chat_session_summary.updated_at IS '摘要最后更新时间';

-- 通用异步任务（通知中心事实源；业务只 submit，完成后 SSE 推前端）
CREATE TABLE IF NOT EXISTS async_job
(
    id            VARCHAR(64) PRIMARY KEY,
    job_type      VARCHAR(64)  NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    title         VARCHAR(200) NOT NULL,
    ref_id        VARCHAR(64),
    payload       TEXT,
    error_message TEXT,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    finished_at   TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_async_job_status ON async_job (status);
CREATE INDEX IF NOT EXISTS idx_async_job_created ON async_job (created_at DESC);
COMMENT ON TABLE async_job IS '通用后台任务：PENDING→RUNNING→SUCCEEDED/FAILED；前端经 SSE 订阅终态';
COMMENT ON COLUMN async_job.job_type IS '任务类型，由业务 Handler 注册，如 kb_ingest';
COMMENT ON COLUMN async_job.status IS 'PENDING / RUNNING / SUCCEEDED / FAILED';
COMMENT ON COLUMN async_job.title IS '通知标题（用户可见）';
COMMENT ON COLUMN async_job.ref_id IS '业务主键，如 document_meta.id；演示任务可空';
COMMENT ON COLUMN async_job.payload IS 'JSON 字符串，handler 入参';
COMMENT ON COLUMN async_job.error_message IS '失败原因（截断）';
COMMENT ON COLUMN async_job.finished_at IS '进入终态的时间';
