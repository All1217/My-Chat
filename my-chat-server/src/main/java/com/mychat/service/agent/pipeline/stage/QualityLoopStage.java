package com.mychat.service.agent.pipeline.stage;

import com.mychat.common.ChatStreamEvent;
import com.mychat.entity.dto.EvaluateOptimizeRequest;
import com.mychat.service.agent.pipeline.ChatTurnContext;
import com.mychat.service.agent.pipeline.ChatTurnEmitter;
import com.mychat.service.agent.pipeline.ChatTurnStage;
import com.mychat.service.agent.quality.AgentEvaluatorOptimizerService;
import com.mychat.utils.WorkspaceUtil;
import com.mychat.utils.WritePathExtractor;
import com.mychat.vo.EvaluateOptimizeResultVO;
import com.mychat.vo.EvaluateOptimizeRoundVO;
import com.mychat.vo.OrchestrateStepVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

/**
 * 可选写盘质量环：解析 write 路径后跑 Evaluator-Optimizer，再推 step + 摘要 delta。
 * <p>
 * 怎么读：{@code qualityLoop=false} 或解析不到路径则内部短路，与旧
 * {@code runQualityLoopIfNeeded} 一致；算法仍在 {@link AgentEvaluatorOptimizerService}。
 */
@Slf4j
@Component
public class QualityLoopStage implements ChatTurnStage {

    private static final String DEFAULT_QUALITY_CRITERIA =
            "文件存在、非空，且内容符合用户目标。";

    private final AgentEvaluatorOptimizerService agentEvaluatorOptimizerService;
    private final WorkspaceUtil workspaceUtil;

    public QualityLoopStage(
            AgentEvaluatorOptimizerService agentEvaluatorOptimizerService,
            WorkspaceUtil workspaceUtil) {
        this.agentEvaluatorOptimizerService = agentEvaluatorOptimizerService;
        this.workspaceUtil = workspaceUtil;
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
        if (!ctx.isQualityLoop()) {
            return Mono.empty();
        }
        return Mono.fromRunnable(() -> runQualityLoop(ctx))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(e -> {
                    log.warn("质量环执行失败 turnId={}: {}", ctx.getTurnId(), e.getMessage());
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
    private void runQualityLoop(ChatTurnContext ctx) {
        List<OrchestrateStepVO> orchSteps = ctx.getOrchestrateResult() != null
                ? ctx.getOrchestrateResult().getSteps()
                : null;
        String path = WritePathExtractor.fromToolEvents(ctx.getAccumulated());
        if (!StringUtils.hasText(path) && orchSteps != null) {
            path = WritePathExtractor.fromOrchestrateSteps(orchSteps);
        }
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
        eoReq.setCriteria(StringUtils.hasText(ctx.getCriteria())
                ? ctx.getCriteria().trim()
                : DEFAULT_QUALITY_CRITERIA);
        String workDir = ctx.getWorkDir();
        eoReq.setWorkDir(StringUtils.hasText(workDir)
                ? workDir
                : workspaceUtil.getWorkspaceRoot().toString());

        log.info("主聊天质量环启动 path={} turnId={}", path, ctx.getTurnId());
        EvaluateOptimizeResultVO eoResult = agentEvaluatorOptimizerService.evaluateOptimize(eoReq);
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
        for (EvaluateOptimizeRoundVO round : eoResult.getRounds()) {
            String reasoning = round.getReasoning() != null ? round.getReasoning() : "";
            String feedback = round.getFeedback() != null ? round.getFeedback() : "";
            String obs = "evaluationPass=" + round.isEvaluationPass()
                    + ", ruleCheckPassed=" + round.isRuleCheckPassed()
                    + (StringUtils.hasText(feedback) ? "\n" + feedback : "");
            ChatTurnEmitter.emit(ctx, ChatStreamEvent.step(
                    ctx.getTurnId(), ctx.getSeq(), i++, "evaluate_optimize", reasoning,
                    "iteration=" + round.getIteration(), obs));
        }
    }
}
