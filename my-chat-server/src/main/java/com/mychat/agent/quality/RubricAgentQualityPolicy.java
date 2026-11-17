package com.mychat.agent.quality;

import com.mychat.agent.api.AgentRunRequest;
import com.mychat.agent.api.AgentRunResult;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/** 使用明确 rubric 评价最终输出，不公开 evaluator reasoning。 */
public class RubricAgentQualityPolicy implements AgentQualityPolicy {

    private final String id;
    private final String defaultRubric;
    private final ChatClient chatClient;

    /** 创建场景 evaluator。 */
    public RubricAgentQualityPolicy(
            String id, String defaultRubric, ChatClient chatClient) {
        this.id = id;
        this.defaultRubric = defaultRubric;
        this.chatClient = chatClient;
    }

    /** 返回策略 ID。 */
    @Override
    public String id() {
        return id;
    }

    /** 对成功输出执行一次结构化评价，并把公开安全指标写入 metrics。 */
    @Override
    public AgentRunResult evaluate(AgentRunRequest request, AgentRunResult result) {
        if (result.status() != AgentRunResult.Status.SUCCEEDED) {
            return result;
        }
        String rubric = String.valueOf(
                request.parameters().getOrDefault("rubric", defaultRubric));
        if (!StringUtils.hasText(rubric)) {
            return withMetrics(result, Map.of(
                    "qualityPolicy", id, "qualitySkipped", true));
        }
        Evaluation evaluation = chatClient.prompt()
                .system("你是结果质量评价器。只输出结构化 pass、score、feedback；不要输出思维过程。")
                .user("评价标准：\n" + rubric + "\n\n待评价结果：\n" + result.output())
                .call()
                .entity(Evaluation.class);
        Evaluation safe = evaluation == null
                ? new Evaluation(false, 0, "评价器未返回结果") : evaluation;
        return withMetrics(result, Map.of(
                "qualityPolicy", id,
                "qualityPassed", safe.pass(),
                "qualityScore", safe.score(),
                "manualReviewRequired", !safe.pass()));
    }

    /** 合并质量指标。 */
    private AgentRunResult withMetrics(
            AgentRunResult result, Map<String, Object> qualityMetrics) {
        Map<String, Object> metrics = new LinkedHashMap<>(result.metrics());
        metrics.putAll(qualityMetrics);
        return new AgentRunResult(
                result.status(), result.output(), result.steps(),
                result.errorMessage(), metrics);
    }

    /** evaluator 结构化输出。 */
    public record Evaluation(boolean pass, int score, String feedback) {
    }
}
