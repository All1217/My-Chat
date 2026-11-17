package com.mychat.agent.action;

import com.mychat.agent.api.AgentAction;
import com.mychat.agent.api.AgentRunRequest;
import com.mychat.agent.api.AgentScenarioId;
import com.mychat.agent.api.AgentStepResult;
import com.mychat.agent.capability.ProfileToolCapability;
import com.mychat.agent.runtime.AgentActionHandler;
import com.mychat.agent.runtime.AgentDecision;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/** MAIN_CHAT 的受限联网查询 Action。 */
@Component
public class SearchActionHandler implements AgentActionHandler {

    private final ProfileToolCapability capability;

    /** 注入受限 Profile 工具能力。 */
    public SearchActionHandler(ProfileToolCapability capability) {
        this.capability = capability;
    }

    /** 返回动作描述。 */
    @Override
    public AgentAction action() {
        return new AgentAction("search", "网页搜索和只读远程信息查询",
                "{\"instruction\":\"string\"}", "text", AgentAction.SideEffectLevel.READ_ONLY);
    }

    /** 执行联网查询。 */
    @Override
    public AgentStepResult handle(AgentRunRequest request, AgentDecision decision, int stepIndex) {
        String message = ActionPromptSupport.message(request, decision);
        String observation = capability.execute(
                AgentScenarioId.MAIN_CHAT,
                request,
                message,
                Set.of("web-search", "mcp"));
        return new AgentStepResult(stepIndex, action().id(), AgentStepResult.Status.SUCCEEDED,
                observation, null, Map.of("toolCalls", 1, "toolName", "web-search"));
    }
}
