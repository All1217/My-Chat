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
    id         VARCHAR(255) PRIMARY KEY,
    user_id    BIGINT,
    title      VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Spring AI 聊天记忆表（1.1.3版本）
CREATE TABLE IF NOT EXISTS spring_ai_chat_memory
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT       default null,
    title           varchar(120) default null,
    conversation_id VARCHAR(255) NOT NULL,
    type            VARCHAR(50)  NOT NULL,
    content         TEXT         NOT NULL,
    timestamp       TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

-- 通用索引创建（H2兼容）
CREATE INDEX IF NOT EXISTS idx_users_username ON users (username);
CREATE INDEX IF NOT EXISTS idx_chat_sessions_user_id ON chat_sessions (user_id);
CREATE INDEX IF NOT EXISTS idx_conversation_id ON spring_ai_chat_memory (conversation_id);
