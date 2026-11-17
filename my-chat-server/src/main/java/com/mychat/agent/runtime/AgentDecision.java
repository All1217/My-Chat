package com.mychat.agent.runtime;

import java.util.Map;

/** 决策器产出的下一步动作或终止答复。 */
public record AgentDecision(
        String actionId,
        String instruction,
        Map<String, Object> parameters,
        boolean finish,
        String finalOutput,
        String reason) {

    /** 固化决策参数并清理空值。 */
    public AgentDecision {
        actionId = actionId == null ? "" : actionId;
        instruction = instruction == null ? "" : instruction;
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        finalOutput = finalOutput == null ? "" : finalOutput;
        reason = reason == null ? "" : reason;
    }

    /** 构造动作决策。 */
    public static AgentDecision action(String actionId, String instruction, Map<String, Object> parameters) {
        return new AgentDecision(actionId, instruction, parameters, false, "", "");
    }

    /** 构造完成决策。 */
    public static AgentDecision finish(String output) {
        return new AgentDecision("", "", Map.of(), true, output, "completed");
    }
}
