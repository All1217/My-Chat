package com.mychat.agent.runtime;

import com.mychat.agent.api.AgentScenarioId;
import com.mychat.agent.api.AgentScenarioProfile;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

/** Phase 2 Demo Profile；不接管主聊天生产路径。 */
@Configuration
public class AgentRuntimeConfiguration {

    /** 主聊天 Demo Profile，不暴露模拟炒股工具。 */
    @Bean
    public AgentScenarioProfile mainChatRuntimeProfile() {
        return new AgentScenarioProfile(
                AgentScenarioId.MAIN_CHAT,
                "phase2-v1",
                Set.of("general", "file", "search", "retrieve_kb"),
                Set.of("file", "web-search", "mcp"),
                6,
                8,
                "text",
                "direct");
    }

    /** 角色扮演 Profile，仅允许无工具推理。 */
    @Bean
    public AgentScenarioProfile roleplayRuntimeProfile() {
        return new AgentScenarioProfile(
                AgentScenarioId.ROLEPLAY,
                "phase2-v1",
                Set.of("general"),
                Set.of(),
                4,
                0,
                "text",
                "direct");
    }
}
