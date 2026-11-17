package com.mychat.service.agent.pipeline.stage;

import com.mychat.common.ChatStreamEvent;
import com.mychat.agent.api.AgentStepResult;
import com.mychat.agent.api.AgentScenarioId;
import com.mychat.agent.api.PublicProgressEvent;
import com.mychat.agent.quality.MainChatQualityPolicy;
import com.mychat.agent.runtime.ScenarioProfileRegistry;
import com.mychat.agent.tool.ChatClientProfileFactory;
import com.mychat.entity.dto.EvaluateOptimizeRequest;
import com.mychat.service.agent.pipeline.ChatTurnContext;
import com.mychat.service.agent.pipeline.ChatTurnEmitter;
import com.mychat.service.agent.pipeline.ChatTurnStage;
import com.mychat.service.agent.quality.AgentEvaluatorOptimizerService;
import com.mychat.utils.WorkspaceUtil;
import com.mychat.utils.WritePathExtractor;
import com.mychat.vo.EvaluateOptimizeResultVO;
import com.mychat.vo.EvaluateOptimizeRoundVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Set;

/**
 * 可选写盘质量环：解析 write 路径后跑 Evaluator-Optimizer，再推 step + 摘要 delta。
 * <p>
 * 怎么读：{@code qualityLoop=false} 或解析不到路径则内部短路，与旧
 * {@code runQualityLoopIfNeeded} 一致；算法仍在 {@link AgentEvaluatorOptimizerService}。
 */
@Slf4j
@Component
public class QualityLoopStage implements ChatTurnStage {

    private final AgentEvaluatorOptimizerService agentEvaluatorOptimizerService;
    private final WorkspaceUtil workspaceUtil;
    private final MainChatQualityPolicy qualityPolicy;
    private final ChatClientProfileFactory clientFactory;
    private final ScenarioProfileRegistry profileRegistry;

    /** 注入质量服务、工作区以及 MAIN_CHAT Profile 客户端。 */
    public QualityLoopStage(
            AgentEvaluatorOptimizerService agentEvaluatorOptimizerService,
            WorkspaceUtil workspaceUtil,
            MainChatQualityPolicy qualityPolicy,
            ChatClientProfileFactory clientFactory,
            ScenarioProfileRegistry profileRegistry) {
        this.agentEvaluatorOptimizerService = agentEvaluatorOptimizerService;
        this.workspaceUtil = workspaceUtil;
        this.qualityPolicy = qualityPolicy;
        this.clientFactory = clientFactory;
        this.profileRegistry = profileRegistry;
    }

    /**
     * 阶段短名。
     */
    @Override
    public String name() {
        return "quality_loop";
    }

    /**
     * 未开启则空完成；开启后在 boundedElastic 执行，失败只记 step 不打断回合。
     */
    @Override
    public Mono<Void> execute(ChatTurnContext ctx) {
        MainChatQualityPolicy.Decision decision = qualityPolicy.decide(ctx);
        if (!decision.run()) {
            return Mono.empty();
        }
        return Mono.fromRunnable(() -> runQualityLoop(
                        ctx, decision.rubric(), decision.maxIterations()))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(e -> {
                    log.warn("质量环执行失败 turnId={}: {}", ctx.getTurnId(), e.getMessage());
                    emitQualityProgress(ctx, "quality-1", AgentStepResult.Status.FAILED,
                            "质量检查失败", "质量环执行异常", 0);
                    ChatTurnEmitter.emit(ctx, ChatStreamEvent.step(
                            ctx.getTurnId(), ctx.getSeq(), 0, "evaluate_optimize",
                            "质量环异常", e.getMessage() != null ? e.getMessage() : "error", null));
                    return Mono.empty();
                })
                .then();
    }

    /**
     * 解析 write 路径 → 调质量环 → 推 rounds 与文末摘要。
     */
    private void runQualityLoop(ChatTurnContext ctx, String rubric, int maxIterations) {
        String path = WritePathExtractor.fromToolEvents(ctx.getAccumulated());
        if (!StringUtils.hasText(path)) {
            path = WritePathExtractor.hintFromText(ctx.getOriginalPrompt());
        }
        if (!StringUtils.hasText(path)) {
            log.warn("qualityLoop=true 但未解析到 write 路径，已跳过质量环 turnId={}", ctx.getTurnId());
            return;
        }

        EvaluateOptimizeRequest eoReq = new EvaluateOptimizeRequest();
        eoReq.setGoal(ctx.getOriginalPrompt());
        eoReq.setPath(path);
        eoReq.setCriteria(rubric);
        eoReq.setMaxIterations(maxIterations);
        String workDir = ctx.getWorkDir();
        eoReq.setWorkDir(StringUtils.hasText(workDir)
                ? workDir
                : workspaceUtil.getWorkspaceRoot("").toString());

        log.info("主聊天质量环启动 path={} turnId={}", path, ctx.getTurnId());
        emitQualityProgress(ctx, "quality-1", AgentStepResult.Status.RUNNING,
                "质量检查", "", 0);
        EvaluateOptimizeResultVO eoResult =
                agentEvaluatorOptimizerService.evaluateOptimizeExplicit(
                        eoReq,
                        clientFactory.create(
                                profileRegistry.require(AgentScenarioId.MAIN_CHAT),
                                Set.of("file")));
        if (eoResult == null || eoResult.getRounds() == null
                || eoResult.getRounds().isEmpty()) {
            emitQualityProgress(ctx, "quality-1", AgentStepResult.Status.FAILED,
                    "质量检查失败", "未产生质量检查结果", 0);
        }
        emitQualityLoopSteps(ctx, eoResult);
        if (eoResult != null) {
            String summary = "\n\n（写盘质量环："
                    + (eoResult.isPassed() ? "已通过" : "未完全通过")
                    + "，原因=" + eoResult.getFinishedReason()
                    + "，path=" + eoResult.getPath() + "）";
            ChatTurnEmitter.emitTextDelta(ctx, summary);
        }
    }

    /**
     * 把质量环每一轮推成 name=evaluate_optimize 的 step。
     */
    private void emitQualityLoopSteps(ChatTurnContext ctx, EvaluateOptimizeResultVO eoResult) {
        if (eoResult == null || eoResult.getRounds() == null) {
            return;
        }
        int i = 1;
        int total = eoResult.getRounds().size();
        for (EvaluateOptimizeRoundVO round : eoResult.getRounds()) {
            String obs = "evaluationPass=" + round.isEvaluationPass()
                    + ", ruleCheckPassed=" + round.isRuleCheckPassed();
            String id = "quality-" + i;
            boolean last = i == total;
            emitQualityProgress(ctx, id,
                    round.isEvaluationPass()
                            ? AgentStepResult.Status.SUCCEEDED
                            : last ? AgentStepResult.Status.FAILED : AgentStepResult.Status.SUCCEEDED,
                    round.isEvaluationPass() ? "质量检查通过"
                            : last ? "质量检查未通过" : "按反馈修订",
                    obs, 0);
            i++;
        }
    }

    /** 发布不含 evaluator reasoning 的质量进度。 */
    private void emitQualityProgress(ChatTurnContext ctx, String stepId,
                                     AgentStepResult.Status status, String displayName,
                                     String summary, long elapsedMs) {
        String runId = ctx.getRunContext() != null
                ? ctx.getRunContext().runId() : ctx.getTurnId();
        PublicProgressEvent progress = new PublicProgressEvent(
                runId,
                runId + "-" + stepId,
                null,
                PublicProgressEvent.Type.QUALITY,
                status,
                displayName,
                summary,
                elapsedMs,
                java.util.Map.of("actionId", "evaluate_optimize"));
        ChatTurnEmitter.emit(ctx,
                ChatStreamEvent.publicProgress(ctx.getTurnId(), ctx.getSeq(), progress));
    }
}
