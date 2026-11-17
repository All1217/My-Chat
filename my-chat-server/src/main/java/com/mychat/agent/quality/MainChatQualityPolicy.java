package com.mychat.agent.quality;

import com.mychat.agent.api.AgentRunResult;
import com.mychat.agent.api.AgentStepResult;
import com.mychat.service.agent.pipeline.ChatTurnContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Set;

/** MAIN_CHAT 质量环触发策略，限制任务类型、副作用和迭代预算。 */
@Component
public class MainChatQualityPolicy {

    private static final Set<String> IMPROVABLE_HINTS = Set.of(
            "代码", "文档", "报告", "研究", "方案", "readme",
            ".md", ".java", ".vue", ".ts", ".py");
    private static final Set<String> HIGH_RISK_HINTS = Set.of(
            "删除", "交易", "下单", "发送", "发布", "付款");

    /** 判断是否运行质量环，并给出 rubric 与最大迭代次数。 */
    public Decision decide(ChatTurnContext ctx) {
        if (!ctx.isQualityLoop()) {
            return Decision.skip("未启用质量环");
        }
        AgentRunResult result = ctx.getAgentRunResult();
        if (result == null || result.status() != AgentRunResult.Status.SUCCEEDED) {
            return Decision.skip("Agent Run 未成功");
        }
        boolean wroteFile = result.steps().stream()
                .anyMatch(step -> "file".equals(step.actionId())
                        && step.status() == AgentStepResult.Status.SUCCEEDED);
        if (!wroteFile) {
            return Decision.skip("没有可改进的文件产物");
        }
        if (!StringUtils.hasText(ctx.getCriteria())) {
            return Decision.skip("缺少明确 rubric");
        }
        String goal = ctx.getOriginalPrompt().toLowerCase(Locale.ROOT);
        if (HIGH_RISK_HINTS.stream().anyMatch(goal::contains)) {
            return Decision.skip("任务包含高风险副作用");
        }
        if (IMPROVABLE_HINTS.stream().noneMatch(goal::contains)) {
            return Decision.skip("任务不属于代码、文档或研究产物");
        }
        long elapsedMs = number(result.metrics().get("elapsedMs"));
        if (elapsedMs >= 150_000L) {
            return Decision.skip("主 Agent 已接近耗时预算");
        }
        long tokenUsage = number(result.metrics().get("tokenUsage"));
        long stepCount = Math.max(result.steps().size(), number(result.metrics().get("stepCount")));
        if (tokenUsage >= 12_000L || stepCount > 4) {
            return Decision.skip("质量环剩余预算不足");
        }
        return new Decision(true, ctx.getCriteria().trim(), 2, "满足质量环条件");
    }

    /** 安全读取耗时指标。 */
    private long number(Object value) {
        return value instanceof Number number ? Math.max(0, number.longValue()) : 0;
    }

    /** 质量策略决定。 */
    public record Decision(boolean run, String rubric, int maxIterations, String reason) {

        /** 创建跳过决定。 */
        public static Decision skip(String reason) {
            return new Decision(false, "", 0, reason);
        }
    }
}
