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
    created_at      TIMESTAMP DEFAULT CURRENT_DATE,     -- DATETIME 改为 TIMESTAMP
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP -- 去掉了 ON UPDATE，PostgreSQL 不支持该写法
);
COMMENT ON TABLE chat_sessions IS '聊天会话总表，用于存储用户与AI的每一次对话会话基本信息';
COMMENT ON COLUMN chat_sessions.conversation_id IS '会话唯一标识符（主键），通常由前端生成UUID或雪花ID';
COMMENT ON COLUMN chat_sessions.user_id IS '发起该会话的用户ID，关联用户主表';
COMMENT ON COLUMN chat_sessions.title IS '会话标题，方便用户历史记录展示';
COMMENT ON COLUMN chat_sessions.kb_id IS '本次会话关联的知识库ID（可为空），用于RAG场景下的知识库隔离';
COMMENT ON COLUMN chat_sessions.created_at IS '会话创建日期（注意：默认值为CURRENT_DATE，粒度只到天，不含具体时分秒）';
COMMENT ON COLUMN chat_sessions.updated_at IS '会话最后更新时间（含时分秒），用于排序活跃会话；因PGSQL无ON UPDATE自动更新，需应用层手动维护';

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
COMMENT ON TABLE vector_store IS '向量存储主表';
COMMENT ON COLUMN vector_store.id IS '主键，UUID自动生成';
COMMENT ON COLUMN vector_store.content IS '待检索的原始文本内容';
COMMENT ON COLUMN vector_store.metadata IS '文档附加元数据（JSONB格式）';
COMMENT ON COLUMN vector_store.embedding IS '1536维向量嵌入值';

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
COMMENT ON TABLE knowledge_base IS '知识库基础信息';
COMMENT ON COLUMN knowledge_base.id IS '知识库唯一ID';
COMMENT ON COLUMN knowledge_base.name IS '知识库名称';
COMMENT ON COLUMN knowledge_base.description IS '知识库描述';
COMMENT ON COLUMN knowledge_base.created_at IS '创建时间';
COMMENT ON COLUMN knowledge_base.updated_at IS '更新时间（应用层维护）';

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
COMMENT ON TABLE document_meta IS '文档元数据及处理状态';
COMMENT ON COLUMN document_meta.id IS '文档唯一ID';
COMMENT ON COLUMN document_meta.kb_id IS '所属知识库ID（外键，级联删除）';
COMMENT ON COLUMN document_meta.filename IS '原始文件名';
COMMENT ON COLUMN document_meta.file_size IS '文件大小（单位：字节）';
COMMENT ON COLUMN document_meta.file_type IS '文件类型（如 PDF/TXT/DOCX）';
COMMENT ON COLUMN document_meta.chunk_count IS '该文档的切片总数';
COMMENT ON COLUMN document_meta.status IS '处理状态（PROCESSING/SUCCESS/FAILED）';
COMMENT ON COLUMN document_meta.created_at IS '创建时间';
COMMENT ON COLUMN document_meta.updated_at IS '更新时间（应用层维护）';

CREATE INDEX IF NOT EXISTS idx_document_meta_kb_id ON document_meta (kb_id);
