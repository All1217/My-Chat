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

/** MAIN_CHAT 的工作区 shell Action。 */
@Component
public class ShellActionHandler implements AgentActionHandler {

    private final ProfileToolCapability capability;

    /** 注入受限 Profile 工具能力。 */
    public ShellActionHandler(ProfileToolCapability capability) {
        this.capability = capability;
    }

    /** 返回动作描述。 */
    @Override
    public AgentAction action() {
        return new AgentAction("shell",
                "在工作区执行一条非交互 shell 命令（构建、git、脚本），不要用它读写文件",
                "{\"instruction\":\"string\"}",
                "text", AgentAction.SideEffectLevel.REVERSIBLE_WRITE);
    }

    /** 在显式工作目录执行命令。 */
    @Override
    public AgentStepResult handle(AgentRunRequest request, AgentDecision decision, int stepIndex) {
        String message = ActionPromptSupport.message(request, decision);
        String observation = capability.execute(
                AgentScenarioId.MAIN_CHAT, request, message, Set.of("shell"));
        return new AgentStepResult(stepIndex, action().id(), AgentStepResult.Status.SUCCEEDED,
                observation, null, Map.of("toolCalls", 1, "toolName", "shell"));
    }
}
