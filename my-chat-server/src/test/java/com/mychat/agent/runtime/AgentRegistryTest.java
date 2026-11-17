package com.mychat.agent.runtime;

import com.mychat.agent.api.AgentAction;
import com.mychat.agent.api.AgentRunRequest;
import com.mychat.agent.api.AgentScenarioId;
import com.mychat.agent.api.AgentScenarioProfile;
import com.mychat.agent.api.AgentStepResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 动态注册表配置错误测试。 */
class AgentRegistryTest {

    /** 验证重复 Action ID 会在启动期失败。 */
    @Test
    void shouldRejectDuplicateActions() {
        AgentActionHandler first = handler("same");
        AgentActionHandler second = handler("same");

        assertThatThrownBy(() -> new ActionRegistry(List.of(first, second)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("same");
    }

    /** 验证 Profile 不可引用未知 Action。 */
    @Test
    void shouldRejectUnknownProfileAction() {
        AgentScenarioProfile profile = new AgentScenarioProfile(
                AgentScenarioId.MAIN_CHAT, "test", Set.of("missing"),
                Set.of(), 2, 0, "text", "direct");
        ScenarioProfileRegistry profiles = new ScenarioProfileRegistry(List.of(profile));

        assertThatThrownBy(() -> new AgentProfileConsistencyChecker(
                profiles, new ActionRegistry(List.of())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing");
    }

    /** 创建无需执行的 Fake Handler。 */
    private AgentActionHandler handler(String id) {
        return new AgentActionHandler() {
            /** 返回动作描述。 */
            @Override
            public AgentAction action() {
                return new AgentAction(id, "", "{}", "text", AgentAction.SideEffectLevel.READ_ONLY);
            }

            /** 测试不执行该方法。 */
            @Override
            public AgentStepResult handle(AgentRunRequest request, AgentDecision decision, int stepIndex) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
