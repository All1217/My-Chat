package com.mychat.agent.tool;

import com.mychat.agent.api.AgentScenarioProfile;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

/** 计算 Profile 白名单与调用方请求的工具交集。 */
@Component
public class ToolPolicy {

    private final ToolCatalog catalog;

    /** 注入工具目录。 */
    public ToolPolicy(ToolCatalog catalog) {
        this.catalog = catalog;
    }

    /** 返回 Profile 实际可见工具；未知工具视为配置错误。 */
    public Set<ToolDescriptor> visibleTools(AgentScenarioProfile profile, Set<String> requestedTools) {
        Set<String> requested = requestedTools == null ? profile.allowedTools() : requestedTools;
        Set<ToolDescriptor> visible = new LinkedHashSet<>();
        for (String id : requested) {
            if (profile.allowedTools().contains(id)) {
                visible.add(catalog.require(id));
            }
        }
        return Set.copyOf(visible);
    }

    /** 校验某工具是否可由场景调用。 */
    public ToolDescriptor requireAllowed(AgentScenarioProfile profile, String toolId) {
        if (!profile.allowedTools().contains(toolId)) {
            throw new SecurityException("场景不允许工具: " + toolId);
        }
        return catalog.require(toolId);
    }
}
