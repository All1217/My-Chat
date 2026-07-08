-- 用户表（如果需要）
CREATE TABLE IF NOT EXISTS users
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    username   VARCHAR(50) UNIQUE NOT NULL,
    email      VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 聊天会话表（如果需要）
CREATE TABLE IF NOT EXISTS chat_sessions
(
    conversation_id VARCHAR(255) PRIMARY KEY,
    user_id         BIGINT,
    title           VARCHAR(100),
    kb_id       VARCHAR(64) DEFAULT NULL,
    created_at      DATETIME DEFAULT CURRENT_DATE,
    updated_at      DATETIME DEFAULT CURRENT_DATE ON UPDATE CURRENT_DATE
);

-- Spring AI 聊天记忆表
CREATE TABLE IF NOT EXISTS spring_ai_chat_memory
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id VARCHAR(255) NOT NULL,
    type            VARCHAR(50)  NOT NULL,
    content         TEXT         NOT NULL,
    timestamp       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    sequence_id     BIGINT
);

CREATE INDEX IF NOT EXISTS idx_users_username ON users (username);
CREATE INDEX IF NOT EXISTS idx_chat_sessions_user_id ON chat_sessions (user_id);
CREATE INDEX IF NOT EXISTS idx_conversation_id ON spring_ai_chat_memory (conversation_id);
CREATE INDEX IF NOT EXISTS SPRING_AI_CHAT_MEMORY_CONVERSATION_ID_SEQUENCE_ID_IDX ON spring_ai_chat_memory (sequence_id, conversation_id);

-- PGSQL版
-- 1. 用户表
CREATE TABLE IF NOT EXISTS users
(
    id         BIGSERIAL PRIMARY KEY, -- AUTO_INCREMENT 改为 BIGSERIAL
    username   VARCHAR(50) UNIQUE NOT NULL,
    email      VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2. 聊天会话表
CREATE TABLE IF NOT EXISTS chat_sessions
(
    conversation_id VARCHAR(255) PRIMARY KEY,
    user_id         BIGINT,
    title           VARCHAR(100),
    kb_id           VARCHAR(64) NULL,
    created_at      TIMESTAMP DEFAULT CURRENT_DATE,     -- DATETIME 改为 TIMESTAMP
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP -- 去掉了 ON UPDATE，PostgreSQL 不支持该写法
);

-- 3. Spring AI 聊天记忆表
CREATE TABLE IF NOT EXISTS spring_ai_chat_memory
(
    id              BIGSERIAL PRIMARY KEY, -- AUTO_INCREMENT 改为 BIGSERIAL
    conversation_id VARCHAR(255) NOT NULL,
    type            VARCHAR(50)  NOT NULL,
    content         TEXT         NOT NULL,
    timestamp       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    sequence_id     BIGINT
);

-- 4. 索引（去掉了反引号，缩短了最后一个索引名以防超长）
CREATE INDEX IF NOT EXISTS idx_users_username ON users (username);
CREATE INDEX IF NOT EXISTS idx_chat_sessions_user_id ON chat_sessions (user_id);
CREATE INDEX IF NOT EXISTS idx_conversation_id ON spring_ai_chat_memory (conversation_id);
CREATE INDEX IF NOT EXISTS idx_memory_sequence_conv ON spring_ai_chat_memory (sequence_id, conversation_id);

-- 向量存储表初始化
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS public.vector_store
(
    id        uuid DEFAULT uuid_generate_v4() PRIMARY KEY,
    content   text,
    -- metadata 对应 Document 类的 metadata 属性，Document --> org.springframework.ai.document;
    metadata  jsonb,
    embedding vector(1536)
);

CREATE INDEX IF NOT EXISTS idx_vector_store_embedding
    ON public.vector_store
    USING hnsw (embedding vector_cosine_ops);

-- knowledge_base（知识库）
CREATE TABLE IF NOT EXISTS knowledge_base
(
    id          VARCHAR(64) PRIMARY KEY,
    name        VARCHAR(200) NOT NULL,
    description TEXT,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- document_meta（文档元数据）
CREATE TABLE IF NOT EXISTS document_meta
(
    id              VARCHAR(64) PRIMARY KEY,
    kb_id           VARCHAR(64)  NOT NULL REFERENCES knowledge_base(id) ON DELETE CASCADE,
    filename        VARCHAR(500) NOT NULL,
    file_size       BIGINT,
    file_type       VARCHAR(50),
    chunk_count     INT       DEFAULT 0,
    status          VARCHAR(20) DEFAULT 'PROCESSING',
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_document_meta_kb_id ON document_meta (kb_id);
