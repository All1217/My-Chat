package com.mychat.agent.runtime;

import com.mychat.agent.api.AgentScenarioId;
import com.mychat.agent.api.AgentScenarioProfile;
import com.mychat.agent.quality.AgentQualityPolicy;
import com.mychat.agent.quality.NoopAgentQualityPolicy;
import com.mychat.agent.quality.RubricAgentQualityPolicy;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

/** 正式 Agent 场景 Profile 配置。 */
@Configuration
public class AgentRuntimeConfiguration {

    /** 正式主聊天 Profile，不暴露模拟炒股工具。 */
    @Bean
    public AgentScenarioProfile mainChatRuntimeProfile() {
        return new AgentScenarioProfile(
                AgentScenarioId.MAIN_CHAT,
                "phase4-v1",
                Set.of("general", "file", "search", "retrieve_kb"),
                Set.of("file", "web-search", "mcp"),
                6,
                8,
                "text",
                "main-chat",
                "orchestrator-workers",
                "main-conversation",
                "main-chat",
                "main-chat");
    }

    /** 角色扮演 Profile，仅允许无工具推理。 */
    @Bean
    public AgentScenarioProfile roleplayRuntimeProfile() {
        return new AgentScenarioProfile(
                AgentScenarioId.ROLEPLAY,
                "phase5-v1",
                Set.of("roleplay"),
                Set.of(),
                4,
                0,
                "text",
                "roleplay",
                "chain",
                "isolated-conversation",
                "roleplay",
                "none");
    }

    /** 市场分析 Profile，使用独立并行网页研究能力。 */
    @Bean
    public AgentScenarioProfile marketAnalysisRuntimeProfile() {
        return new AgentScenarioProfile(
                AgentScenarioId.MARKET_ANALYSIS,
                "phase5-v1",
                Set.of("market_research", "general"),
                Set.of("web-search"),
                6,
                6,
                "market_analysis",
                "market-evidence",
                "orchestrator-workers",
                "ephemeral",
                "market-analysis",
                "explicit-rubric");
    }

    /** 面试官 Profile，固定阶段内动态追问并结构化评分。 */
    @Bean
    public AgentScenarioProfile interviewerRuntimeProfile() {
        return new AgentScenarioProfile(
                AgentScenarioId.INTERVIEWER,
                "phase5-v1",
                Set.of("interview"),
                Set.of(),
                5,
                0,
                "interview_assessment",
                "interview-score",
                "chain",
                "isolated-conversation",
                "interviewer",
                "score-review");
    }

    /** 模拟炒股搜集员 Profile，仅暴露只读研究工具。 */
    @Bean
    public AgentScenarioProfile paperResearchRuntimeProfile() {
        return new AgentScenarioProfile(
                AgentScenarioId.PAPER_RESEARCH,
                "phase5-v1",
                Set.of("paper_research", "general"),
                Set.of("paper-market", "paper-research-feed", "paper-strategy",
                        "web-search", "mcp-crawler"),
                8,
                10,
                "research_card_batch",
                "paper-research",
                "orchestrator-workers",
                "ephemeral",
                "paper-research",
                "explicit-rubric");
    }

    /** 模拟炒股交易员 Profile，只能读取决策和轮次，不能执行订单。 */
    @Bean
    public AgentScenarioProfile paperTraderRuntimeProfile() {
        return new AgentScenarioProfile(
                AgentScenarioId.PAPER_TRADER,
                "phase5-v1",
                Set.of("paper_trade_review", "general"),
                Set.of("paper-decision", "paper-run", "paper-market", "paper-strategy"),
                5,
                5,
                "trade_decision_batch",
                "paper-trader",
                "chain",
                "ephemeral",
                "paper-trader",
                "score-review");
    }

    /** 市场证据回答策略。 */
    @Bean
    public AnswerPolicy marketEvidenceAnswerPolicy() {
        return new ObservationAnswerPolicy("market-evidence");
    }

    /** 角色扮演回答策略。 */
    @Bean
    public AnswerPolicy roleplayAnswerPolicy() {
        return new ObservationAnswerPolicy("roleplay");
    }

    /** 面试评分回答策略。 */
    @Bean
    public AnswerPolicy interviewScoreAnswerPolicy() {
        return new ObservationAnswerPolicy("interview-score");
    }

    /** 搜集员类型化回答策略：优先 paper_research 观察，不依赖 finalOutput 批次。 */
    @Bean
    public AnswerPolicy paperResearchAnswerPolicy() {
        return new PaperBatchAnswerPolicy("paper-research", Set.of("paper_research"));
    }

    /** 交易员类型化回答策略：优先 paper_trade_review 观察。 */
    @Bean
    public AnswerPolicy paperTraderAnswerPolicy() {
        return new PaperBatchAnswerPolicy("paper-trader", Set.of("paper_trade_review"));
    }

    /** 无额外评价的默认质量策略。 */
    @Bean
    public AgentQualityPolicy noneAgentQualityPolicy() {
        return new NoopAgentQualityPolicy("none");
    }

    /** 主聊天质量环仍由管道阶段执行。 */
    @Bean
    public AgentQualityPolicy mainChatAgentQualityPolicy() {
        return new NoopAgentQualityPolicy("main-chat");
    }

    /** 有明确 rubric 时执行一次结构化 evaluator。 */
    @Bean
    public AgentQualityPolicy explicitRubricAgentQualityPolicy(
            @Qualifier("agentWorkflowChatClient") ChatClient chatClient) {
        return new RubricAgentQualityPolicy("explicit-rubric", "", chatClient);
    }

    /** 对评分输出执行结构化复核并标记人工复核状态。 */
    @Bean
    public AgentQualityPolicy scoreReviewAgentQualityPolicy(
            @Qualifier("agentWorkflowChatClient") ChatClient chatClient) {
        return new RubricAgentQualityPolicy(
                "score-review", "评分字段完整、依据可复核且风险项明确。", chatClient);
    }
}
