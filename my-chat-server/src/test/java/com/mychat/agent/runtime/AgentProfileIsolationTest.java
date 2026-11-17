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
        assertThat(profile.allowedActions()).containsExactly("roleplay");
        assertThat(profile.memoryPolicy()).isEqualTo("isolated-conversation");
        assertThat(profile.qualityPolicy()).isEqualTo("none");
    }

    /** MARKET_ANALYSIS 只看到网页研究，不依赖主聊天 search 动作。 */
    @Test
    void marketAnalysisShouldUseDedicatedResearchCapability() {
        AgentScenarioProfile profile = configuration.marketAnalysisRuntimeProfile();

        assertThat(profile.allowedActions()).contains("market_research", "general")
                .doesNotContain("search", "file");
        assertThat(profile.allowedTools()).containsExactly("web-search");
        assertThat(profile.qualityPolicy()).isEqualTo("explicit-rubric");
    }

    /** 模拟交易两个 Profile 均不可看到写工具、文件和通用 MCP。 */
    @Test
    void paperProfilesShouldExposeOnlyReadOnlyDomainTools() {
        AgentScenarioProfile research = configuration.paperResearchRuntimeProfile();
        AgentScenarioProfile trader = configuration.paperTraderRuntimeProfile();

        assertThat(research.allowedTools())
                .contains("paper-market", "paper-research-feed", "paper-strategy",
                        "web-search", "mcp-crawler")
                .doesNotContain("paper-note", "file", "mcp", "paper-stage");
        assertThat(trader.allowedTools())
                .contains("paper-decision", "paper-run", "paper-market", "paper-strategy")
                .doesNotContain("paper-stage", "paper-note", "file", "mcp",
                        "web-search", "mcp-crawler");
    }

    /** 面试官使用独立上下文、Memory、质量和回答策略。 */
    @Test
    void interviewerShouldDeclareIndependentPolicies() {
        AgentScenarioProfile profile = configuration.interviewerRuntimeProfile();

        assertThat(profile.contextPolicy()).isEqualTo("interviewer");
        assertThat(profile.memoryPolicy()).isEqualTo("isolated-conversation");
        assertThat(profile.qualityPolicy()).isEqualTo("score-review");
        assertThat(profile.answerPolicy()).isEqualTo("interview-score");
    }
}
