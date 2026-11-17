package com.mychat.agent.tool;

import com.mychat.agent.api.AgentScenarioProfile;
import com.mychat.config.ChatClientRegistry;
import com.mychat.mcp.McpToolBridge;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Set;

/** 从无工具工作流 Client 构建仅暴露 Profile 白名单的 ChatClient。 */
@Component
public class ChatClientProfileFactory {

    private final ChatClientRegistry registry;
    private final ToolPolicy toolPolicy;
    private final ObjectProvider<McpToolBridge> mcpBridge;

    /** 注入模型注册表、工具策略和可选 MCP 桥。 */
    public ChatClientProfileFactory(ChatClientRegistry registry, ToolPolicy toolPolicy,
                                    ObjectProvider<McpToolBridge> mcpBridge) {
        this.registry = registry;
        this.toolPolicy = toolPolicy;
        this.mcpBridge = mcpBridge;
    }

    /** 创建只挂载实际可见工具的客户端。 */
    public ChatClient create(AgentScenarioProfile profile) {
        return create(profile, profile.allowedTools());
    }

    /** 创建只挂载 Profile 与动作共同允许工具的客户端。 */
    public ChatClient create(AgentScenarioProfile profile, Set<String> actionTools) {
        ChatClient.Builder builder = registry.workflow().mutate()
                .defaultSystem("只可调用当前挂载的白名单工具；不可声称拥有未挂载工具。");
        Set<ToolDescriptor> visible = toolPolicy.visibleTools(profile, actionTools);
        for (ToolDescriptor descriptor : visible) {
            if ("mcp".equals(descriptor.id())) {
                McpToolBridge bridge = mcpBridge.getIfAvailable();
                if (bridge != null) {
                    bridge.attachTo(builder);
                }
            } else if ("mcp-crawler".equals(descriptor.id())) {
                McpToolBridge bridge = mcpBridge.getIfAvailable();
                if (bridge != null) {
                    bridge.attachTo(builder, Set.of(
                            "mcp-crawler-server", "mcp-server-crawler", "crawler"));
                }
            } else if (descriptor.toolObject() != null) {
                builder.defaultTools(descriptor.toolObject());
            }
        }
        return builder.build();
    }
}
