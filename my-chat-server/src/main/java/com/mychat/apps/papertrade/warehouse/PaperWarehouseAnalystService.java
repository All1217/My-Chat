package com.mychat.apps.papertrade.warehouse;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mychat.apps.papertrade.PaperTradeProperties;
import com.mychat.apps.papertrade.agent.PaperAgentRuntimeAdapter;
import com.mychat.apps.papertrade.research.entity.po.PaperResearchNote;
import com.mychat.apps.papertrade.research.mapper.PaperResearchNoteMapper;
import com.mychat.apps.papertrade.trader.PaperAiContextBuilder;
import com.mychat.apps.papertrade.trader.PaperStrategyService;
import com.mychat.apps.papertrade.trader.entity.po.PaperStrategy;
import com.mychat.apps.papertrade.trader.entity.vo.PaperAiSubmitVO;
import com.mychat.apps.papertrade.warehouse.entity.po.PaperWarehouseAnalystRun;
import com.mychat.apps.papertrade.warehouse.mapper.PaperWarehouseAnalystRunMapper;
import com.mychat.apps.papertrade.workflow.PaperRunLifecycle;
import com.mychat.apps.papertrade.workflow.PaperWorkflowStep;
import com.mychat.apps.papertrade.workflow.PaperWorkflowTrace;
import com.mychat.job.AsyncJobService;
import com.mychat.vo.AsyncJobVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 票仓分析员：遍历 value_tier=2，Agent 深搜后 updateValueTier。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaperWarehouseAnalystService {

    public static final String JOB_TYPE = "paper_trade_warehouse_analyst";

    private final PaperWarehouseAnalystRunMapper runMapper;
    private final PaperResearchNoteMapper noteMapper;
    private final PaperTradeProperties properties;
    private final PaperStrategyService strategyService;
    private final PaperAiContextBuilder contextBuilder;
    private final PaperAgentRuntimeAdapter agentRuntime;
    private final AsyncJobService asyncJobService;
    private final PaperRunLifecycle lifecycle;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public PaperAiSubmitVO submitRun() {
        lifecycle.requireNoActive(runMapper, q -> q.in(
                PaperWarehouseAnalystRun::getStatus,
                PaperRunLifecycle.activeStatuses(
                        PaperWarehouseAnalystRun.STATUS_PENDING,
                        PaperWarehouseAnalystRun.STATUS_RUNNING)));
        PaperWarehouseAnalystRun run = new PaperWarehouseAnalystRun();
        run.setId(UUID.randomUUID().toString().replace("-", ""));
        run.setStatus(PaperWarehouseAnalystRun.STATUS_PENDING);
        run.setScreenedCount(0);
        run.setAdjustedCount(0);
        runMapper.insert(run);
        AsyncJobVO job = asyncJobService.submit(JOB_TYPE, "票仓分析员一轮", run.getId(), null, true);
        run.setJobId(job.getId());
        runMapper.updateById(run);
        PaperAiSubmitVO vo = new PaperAiSubmitVO();
        vo.setRunId(run.getId());
        vo.setJobId(job.getId());
        vo.setStatus(run.getStatus());
        return vo;
    }

    public PaperWarehouseAnalystRun getRun(String id) {
        PaperWarehouseAnalystRun run = runMapper.selectById(id);
        if (run == null) {
            throw new IllegalArgumentException("分析员轮次不存在");
        }
        return run;
    }

    public void run(String runId) throws Exception {
        if (!StringUtils.hasText(runId)) {
            throw new IllegalArgumentException("paper_trade_warehouse_analyst 缺少 refId");
        }
        PaperWarehouseAnalystRun run = runMapper.selectById(runId.trim());
        if (run == null) {
            throw new IllegalArgumentException("分析员轮次不存在");
        }
        boolean marked = lifecycle.tryMarkRunning(runMapper, u -> u
                .eq(PaperWarehouseAnalystRun::getId, runId.trim())
                .eq(PaperWarehouseAnalystRun::getStatus, PaperWarehouseAnalystRun.STATUS_PENDING)
                .set(PaperWarehouseAnalystRun::getStatus, PaperWarehouseAnalystRun.STATUS_RUNNING));
        if (!marked) {
            log.info("分析员轮次已非 PENDING，跳过 runId={}", runId);
            return;
        }
        PaperWorkflowTrace trace = new PaperWorkflowTrace();
        try {
            trace.ok(PaperWorkflowStep.START_RUN, "jobId=" + run.getJobId());
            int batch = Math.max(1, properties.getResearchBatchSize());
            List<PaperResearchNote> tier2 = noteMapper.selectList(new LambdaQueryWrapper<PaperResearchNote>()
                    .eq(PaperResearchNote::getValueTier, 2)
                    .orderByAsc(PaperResearchNote::getCreatedAt)
                    .last("LIMIT " + batch));
            run.setScreenedCount(tier2.size());
            trace.ok(PaperWorkflowStep.LIST_TIER2, "count=" + tier2.size());
            if (tier2.isEmpty()) {
                run.setAdjustedCount(0);
                run.setFrameworkTrace(trace.toJson(objectMapper));
                run.setStatus(PaperWarehouseAnalystRun.STATUS_SUCCEEDED);
                run.setFinishedAt(LocalDateTime.now());
                runMapper.updateById(run);
                trace.ok(PaperWorkflowStep.FINISH_RUN, "empty");
                return;
            }

            PaperStrategy strategy = strategyService.requireDefault();
            String strategyClip = strategy.getContent() == null ? "" : strategy.getContent();
            if (strategyClip.length() > 4000) {
                strategyClip = strategyClip.substring(0, 4000);
            }
            PaperAiContextBuilder.BuiltContext ctx = contextBuilder.build();
            String accountSnippet = clip(ctx.contextJson(), 6000);

            int adjusted = 0;
            ArrayNode perSymbol = objectMapper.createArrayNode();
            for (PaperResearchNote note : tier2) {
                int before = note.getValueTier() == null ? 2 : note.getValueTier();
                String prompt = buildPrompt(note, strategyClip, accountSnippet);
                PaperAgentRuntimeAdapter.Outcome outcome =
                        agentRuntime.warehouseAnalyze(prompt, run.getId(), run.getJobId());
                trace.bindAgentRun(outcome.agentRunId());
                PaperResearchNote after = noteMapper.selectById(note.getSymbol());
                int afterTier = after == null || after.getValueTier() == null
                        ? before : after.getValueTier();
                ObjectNode row = perSymbol.addObject();
                row.put("symbol", note.getSymbol());
                row.put("from", before);
                row.put("to", afterTier);
                row.put("agentRunId", outcome.agentRunId() == null ? "" : outcome.agentRunId());
                if (afterTier != before) {
                    adjusted++;
                }
            }
            trace.ok(PaperWorkflowStep.AI_DEEP_GATHER, "symbols=" + tier2.size());
            trace.ok(PaperWorkflowStep.APPLY_TIER_UPDATE, "adjusted=" + adjusted);

            ObjectNode stats = objectMapper.createObjectNode();
            stats.put("screened", tier2.size());
            stats.put("adjusted", adjusted);
            stats.set("symbols", perSymbol);
            run.setStatsJson(objectMapper.writeValueAsString(stats));
            run.setAdjustedCount(adjusted);
            run.setFrameworkTrace(trace.toJson(objectMapper));
            run.setStatus(PaperWarehouseAnalystRun.STATUS_SUCCEEDED);
            run.setFinishedAt(LocalDateTime.now());
            runMapper.updateById(run);
            trace.ok(PaperWorkflowStep.FINISH_RUN, "adjusted=" + adjusted);
        } catch (Exception e) {
            run.setFrameworkTrace(trace.toJson(objectMapper));
            run.setStatus(PaperWarehouseAnalystRun.STATUS_FAILED);
            run.setErrorMessage(clip(e.getMessage(), 1000));
            run.setFinishedAt(LocalDateTime.now());
            runMapper.updateById(run);
            throw e;
        }
    }

    private String buildPrompt(PaperResearchNote note, String strategy, String accountSnippet)
            throws Exception {
        ObjectNode card = objectMapper.createObjectNode();
        card.put("symbol", note.getSymbol());
        card.put("name", note.getName() == null ? "" : note.getName());
        card.put("story", note.getStory() == null ? "" : note.getStory());
        card.put("risks", note.getRisks() == null ? "" : note.getRisks());
        card.put("valueTier", note.getValueTier() == null ? 2 : note.getValueTier());
        card.put("fitScore", note.getFitScore() == null ? 0 : note.getFitScore().doubleValue());
        card.put("evidenceScore",
                note.getEvidenceScore() == null ? 0 : note.getEvidenceScore().doubleValue());
        card.put("quoteSummary", note.getQuoteSummary() == null ? "" : note.getQuoteSummary());
        return """
                你是票仓分析员。当前标的 valueTier=2（继续观察）。
                用最多 3 步深搜（paper-market / paper-research-feed / paper-strategy / web-search），
                然后必须调用 updateValueTier(symbol, tier)：
                - 1：应该买入；2：继续观察；3：移出票仓。
                禁止改成 0。等级变更以工具成功为准；finalOutput 只写短确认 OK。
                【策略摘要】
                %s
                【账户/持仓片段】
                %s
                【本票卡片】
                %s
                """.formatted(strategy, accountSnippet, objectMapper.writeValueAsString(card));
    }

    private static String clip(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max);
    }
}
