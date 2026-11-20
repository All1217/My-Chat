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
                .contains("file", "web-search", "mcp", "shell")
                .noneMatch(tool -> tool.startsWith("paper-"));
        assertThat(profile.allowedActions()).contains("shell");
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
                .doesNotContain("search", "file", "shell");
        assertThat(profile.allowedTools()).containsExactly("web-search");
        assertThat(profile.qualityPolicy()).isEqualTo("explicit-rubric");
    }

    /** 搜集员/交易员可挂写工具；仍隔离文件与通用 MCP。 */
    @Test
    void paperProfilesShouldExposeDomainWriteToolsWithoutFileOrGenericMcp() {
        AgentScenarioProfile research = configuration.paperResearchRuntimeProfile();
        AgentScenarioProfile trader = configuration.paperTraderRuntimeProfile();
        AgentScenarioProfile analyst = configuration.paperWarehouseAnalystRuntimeProfile();
        AgentScenarioProfile evaluator = configuration.paperTradeEvaluatorRuntimeProfile();

        assertThat(research.allowedTools())
                .contains("paper-market", "paper-research-feed", "paper-strategy",
                        "paper-note-batch", "web-search", "mcp-crawler")
                .doesNotContain("paper-note", "file", "shell", "mcp", "paper-stage",
                        "paper-decision-write", "paper-trade-exec");
        assertThat(trader.allowedTools())
                .contains("paper-decision", "paper-decision-write", "paper-trade-exec",
                        "paper-run", "paper-market", "paper-strategy")
                .doesNotContain("paper-stage", "paper-note", "paper-note-batch", "file", "shell", "mcp",
                        "web-search", "mcp-crawler");
        assertThat(analyst.allowedActions()).contains("paper_warehouse_analyze", "general");
        assertThat(analyst.allowedTools())
                .contains("paper-market", "paper-research-feed", "paper-strategy",
                        "paper-note", "web-search")
                .doesNotContain("paper-trade-exec", "file", "shell", "mcp");
        assertThat(evaluator.allowedTools()).doesNotContain("shell", "file");
        assertThat(evaluator.allowedActions()).doesNotContain("shell");
        assertThat(analyst.maxSteps()).isEqualTo(4);
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
