package com.mychat.mcp.settings.entity.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 设置页一条 MCP/搜索连接。 */
@Data
public class McpToolCatalogItemVO {
    private String id;
    private String name;
    /** local-mcp | remote-mcp | rest */
    private String type;
    private String description;
    private Boolean enabled;
    private List<String> tools = new ArrayList<>();
    private String updatedAt;
}
