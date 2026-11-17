package com.mychat.agent.runtime;

import com.mychat.agent.api.AgentScenarioId;
import com.mychat.agent.api.AgentScenarioProfile;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** 按场景解析并校验 Agent Profile。 */
@Component
public class ScenarioProfileRegistry {

    private final Map<AgentScenarioId, AgentScenarioProfile> profiles;

    /** 收集 Profile；动作引用一致性由 {@link AgentProfileConsistencyChecker} 校验。 */
    public ScenarioProfileRegistry(List<AgentScenarioProfile> profiles) {
        Map<AgentScenarioId, AgentScenarioProfile> indexed = new EnumMap<>(AgentScenarioId.class);
        for (AgentScenarioProfile profile : profiles == null ? List.<AgentScenarioProfile>of() : profiles) {
            if (indexed.putIfAbsent(profile.scenarioId(), profile) != null) {
                throw new IllegalStateException("重复 Agent Profile: " + profile.scenarioId());
            }
        }
        this.profiles = Map.copyOf(indexed);
    }

    /** 获取场景 Profile。 */
    public AgentScenarioProfile require(AgentScenarioId scenarioId) {
        AgentScenarioProfile profile = profiles.get(scenarioId);
        if (profile == null) {
            throw new IllegalArgumentException("未注册 Agent Profile: " + scenarioId);
        }
        return profile;
    }

    /** 返回已注册 Profile 快照。 */
    public Collection<AgentScenarioProfile> all() {
        return profiles.values();
    }
}
