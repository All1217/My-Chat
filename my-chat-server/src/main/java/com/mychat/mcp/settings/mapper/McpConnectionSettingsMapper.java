package com.mychat.mcp.settings.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mychat.mcp.settings.entity.po.McpConnectionSettings;
import org.apache.ibatis.annotations.Mapper;

/** mcp_connection_settings 表访问。 */
@Mapper
public interface McpConnectionSettingsMapper extends BaseMapper<McpConnectionSettings> {
}
