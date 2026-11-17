-- MCP / 联网搜索连接运行时开关（设置页可改，立即生效）。不改 V1–V9。

SET search_path TO public;

CREATE TABLE mcp_connection_settings
(
    id         VARCHAR(64) PRIMARY KEY,
    enabled    BOOLEAN   NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE mcp_connection_settings IS 'MCP/搜索连接是否启用（全局，运行时缓存）';
COMMENT ON COLUMN mcp_connection_settings.id IS '连接 id：mcp-database-server / mcp-crawler-server / smithery-toolbox / web-search-exa';
COMMENT ON COLUMN mcp_connection_settings.enabled IS '是否启用';
COMMENT ON COLUMN mcp_connection_settings.updated_at IS '更新时间';

INSERT INTO mcp_connection_settings (id, enabled)
VALUES ('mcp-database-server', TRUE),
       ('mcp-crawler-server', TRUE),
       ('smithery-toolbox', TRUE),
       ('web-search-exa', TRUE)
ON CONFLICT (id) DO NOTHING;
