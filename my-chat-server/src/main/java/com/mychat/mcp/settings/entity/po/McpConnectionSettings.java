package com.mychat.mcp.settings.entity.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** MCP/搜索连接运行时开关。 */
@TableName("mcp_connection_settings")
@Data
public class McpConnectionSettings {

    public static final String ID_DATABASE = "mcp-database-server";
    public static final String ID_CRAWLER = "mcp-crawler-server";
    public static final String ID_SMITHERY = "smithery-toolbox";
    public static final String ID_EXA = "web-search-exa";

    @TableId(type = IdType.INPUT)
    private String id;

    private Boolean enabled;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
