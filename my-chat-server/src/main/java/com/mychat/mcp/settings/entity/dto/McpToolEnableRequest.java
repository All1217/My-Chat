package com.mychat.mcp.settings.entity.dto;

import lombok.Data;

/** 更新某条连接启用状态。 */
@Data
public class McpToolEnableRequest {
    private Boolean enabled;
}
