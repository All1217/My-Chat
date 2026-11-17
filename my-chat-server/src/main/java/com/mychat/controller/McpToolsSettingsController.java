package com.mychat.controller;

import com.mychat.common.result.Result;
import com.mychat.mcp.settings.McpRuntimeSettingsService;
import com.mychat.mcp.settings.entity.dto.McpToolEnableRequest;
import com.mychat.mcp.settings.entity.vo.McpToolCatalogItemVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 大厅设置：MCP / Exa 连接开关。 */
@RestController
@RequestMapping("/ai/settings/mcp-tools")
@RequiredArgsConstructor
public class McpToolsSettingsController {

    private final McpRuntimeSettingsService settingsService;

    /** 列出已引入的 MCP/搜索连接。 */
    @GetMapping
    public Result<List<McpToolCatalogItemVO>> list() {
        return Result.ok(settingsService.listCatalog());
    }

    /** 更新启用状态，立即影响下一轮工具发现。 */
    @PutMapping("/{id}")
    public Result<McpToolCatalogItemVO> update(@PathVariable String id,
                                               @RequestBody McpToolEnableRequest request) {
        try {
            boolean enabled = request != null && Boolean.TRUE.equals(request.getEnabled());
            return Result.ok(settingsService.setEnabled(id, enabled));
        } catch (IllegalArgumentException e) {
            return Result.fail(400, e.getMessage());
        }
    }
}
