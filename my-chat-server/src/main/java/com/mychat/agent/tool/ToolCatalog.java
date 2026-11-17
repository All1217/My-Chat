package com.mychat.agent.tool;

import com.mychat.agent.api.AgentAction;
import com.mychat.apps.papertrade.tools.PaperDecisionTools;
import com.mychat.apps.papertrade.tools.PaperResearchNoteTools;
import com.mychat.apps.papertrade.tools.PaperResearchRunTools;
import com.mychat.apps.papertrade.tools.PaperStageSummaryTools;
import com.mychat.tools.FileTools;
import com.mychat.tools.WebSearchTools;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 统一登记本地工具组；MCP 作为独立虚拟工具组登记。 */
@Component
public class ToolCatalog {

    private final Map<String, ToolDescriptor> tools;

    /** 登记当前系统工具并拒绝重名。 */
    public ToolCatalog(FileTools fileTools, WebSearchTools webSearchTools,
                       PaperStageSummaryTools stageTools, PaperDecisionTools decisionTools,
                       PaperResearchRunTools runTools, PaperResearchNoteTools noteTools) {
        List<ToolDescriptor> descriptors = List.of(
                descriptor("file", "workspace", AgentAction.SideEffectLevel.HIGH_RISK_WRITE, fileTools),
                descriptor("web-search", "search", AgentAction.SideEffectLevel.READ_ONLY, webSearchTools),
                descriptor("mcp", "remote", AgentAction.SideEffectLevel.READ_ONLY, null),
                descriptor("paper-stage", "papertrade", AgentAction.SideEffectLevel.REVERSIBLE_WRITE, stageTools),
                descriptor("paper-decision", "papertrade", AgentAction.SideEffectLevel.READ_ONLY, decisionTools),
                descriptor("paper-run", "papertrade", AgentAction.SideEffectLevel.READ_ONLY, runTools),
                descriptor("paper-note", "papertrade", AgentAction.SideEffectLevel.REVERSIBLE_WRITE, noteTools));
        Map<String, ToolDescriptor> indexed = new LinkedHashMap<>();
        for (ToolDescriptor descriptor : descriptors) {
            if (indexed.putIfAbsent(descriptor.id(), descriptor) != null) {
                throw new IllegalStateException("Duplicate tool: " + descriptor.id());
            }
        }
        this.tools = Map.copyOf(indexed);
    }

    /** 获取工具描述。 */
    public ToolDescriptor require(String id) {
        ToolDescriptor descriptor = tools.get(id);
        if (descriptor == null) {
            throw new IllegalArgumentException("Unknown tool: " + id);
        }
        return descriptor;
    }

    /** 返回已登记工具 ID。 */
    public Set<String> ids() {
        return tools.keySet();
    }

    /** 创建工具描述。 */
    private ToolDescriptor descriptor(String id, String category,
                                      AgentAction.SideEffectLevel level, Object toolObject) {
        return new ToolDescriptor(id, category, level, toolObject);
    }
}
