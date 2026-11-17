package com.mychat.agent.runtime;

import com.mychat.agent.api.AgentScenarioProfile;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 场景 Profile 工具隔离测试。 */
class AgentProfileIsolationTest {

    private final AgentRuntimeConfiguration configuration = new AgentRuntimeConfiguration();

    /** MAIN_CHAT 不可看到 papertrade 工具。 */
    @Test
    void mainChatShouldNotExposePaperTradeTools() {
        AgentScenarioProfile profile = configuration.mainChatRuntimeProfile();

        assertThat(profile.allowedTools())
                .contains("file", "web-search", "mcp")
                .noneMatch(tool -> tool.startsWith("paper-"));
    }

    /** ROLEPLAY 不可看到文件、搜索或 MCP。 */
    @Test
    void roleplayShouldNotExposeExternalTools() {
        AgentScenarioProfile profile = configuration.roleplayRuntimeProfile();

        assertThat(profile.allowedTools()).isEmpty();
        assertThat(profile.allowedActions()).containsExactly("general");
    }
}
