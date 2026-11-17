package com.mychat.agent.runtime;

import com.mychat.agent.api.AgentScenarioProfile;
import org.springframework.stereotype.Component;

/** 启动期校验 Profile 与 Action 注册一致性，避免注册表互相构造依赖。 */
@Component
public class AgentProfileConsistencyChecker {

    /** 确认每个 Profile 引用的动作均已注册。 */
    public AgentProfileConsistencyChecker(
            ScenarioProfileRegistry profiles, ActionRegistry actionRegistry) {
        for (AgentScenarioProfile profile : profiles.all()) {
            for (String actionId : profile.allowedActions()) {
                if (!actionRegistry.contains(actionId)) {
                    throw new IllegalStateException(
                            "Profile " + profile.scenarioId()
                                    + " 引用了未注册动作 " + actionId);
                }
            }
        }
    }
}
