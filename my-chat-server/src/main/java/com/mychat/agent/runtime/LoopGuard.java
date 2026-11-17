package com.mychat.agent.runtime;

import com.mychat.agent.api.AgentStepResult;
import org.springframework.stereotype.Component;

import java.util.List;

/** 检测重复动作和无新增观察，避免循环空转。 */
@Component
public class LoopGuard {

    /** 连续两次相同动作/指令或相同观察时返回阻断原因。 */
    public String detect(AgentDecision decision, List<AgentDecision> decisions, List<AgentStepResult> steps) {
        if (!decisions.isEmpty()) {
            AgentDecision previous = decisions.get(decisions.size() - 1);
            if (previous.actionId().equals(decision.actionId())
                    && previous.instruction().equals(decision.instruction())) {
                return "检测到重复动作与指令";
            }
        }
        if (steps.size() >= 2) {
            String latest = steps.get(steps.size() - 1).observation();
            String previous = steps.get(steps.size() - 2).observation();
            if (!latest.isBlank() && latest.equals(previous)) {
                return "连续步骤未产生新增信息";
            }
        }
        return "";
    }
}
