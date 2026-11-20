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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 票仓分析员：遍历 value_tier=2，Agent 深搜后 updateValueTier。
 * 单票失败不拖死整轮；有成功处理则整轮 SUCCEEDED。
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
            persistProgress(run, trace, List.of());
            if (tier2.isEmpty()) {
                run.setAdjustedCount(0);
                run.setStatus(PaperWarehouseAnalystRun.STATUS_SUCCEEDED);
                run.setFinishedAt(LocalDateTime.now());
                trace.ok(PaperWorkflowStep.FINISH_RUN, "empty");
                persistProgress(run, trace, List.of());
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
            int okCount = 0;
            List<String> failedSymbols = new ArrayList<>();
            ArrayNode perSymbol = objectMapper.createArrayNode();
            List<ObjectNode> agentTraces = new ArrayList<>();
            int index = 0;
            for (PaperResearchNote note : tier2) {
                index++;
                int before = note.getValueTier() == null ? 2 : note.getValueTier();
                ObjectNode row = perSymbol.addObject();
                row.put("symbol", note.getSymbol());
                row.put("from", before);
                ObjectNode agentRow = objectMapper.createObjectNode();
                agentRow.put("symbol", note.getSymbol());
                try {
                    String prompt = buildPrompt(note, strategyClip, accountSnippet);
                    PaperAgentRuntimeAdapter.Outcome outcome =
                            agentRuntime.warehouseAnalyze(prompt, run.getId(), run.getJobId());
                    if (StringUtils.hasText(outcome.agentRunId())) {
                        trace.bindAgentRun(outcome.agentRunId());
                        row.put("agentRunId", outcome.agentRunId());
                        agentRow.put("agentRunId", outcome.agentRunId());
                    } else {
                        row.put("agentRunId", "");
                    }
                    if (StringUtils.hasText(outcome.traceJson())) {
                        agentRow.set("trace", objectMapper.readTree(outcome.traceJson()));
                    }
                    agentRow.put("ok", true);
                    PaperResearchNote after = noteMapper.selectById(note.getSymbol());
                    int afterTier = after == null || after.getValueTier() == null
                            ? before : after.getValueTier();
                    row.put("to", afterTier);
                    row.put("ok", true);
                    okCount++;
                    if (afterTier != before) {
                        adjusted++;
                    }
                } catch (Exception symbolError) {
                    // 单票隔离：记失败后继续后续标的
                    log.warn("票仓分析单票失败 symbol={}: {}", note.getSymbol(), symbolError.getMessage());
                    row.put("to", before);
                    row.put("ok", false);
                    row.put("agentRunId", "");
                    row.put("error", clip(symbolError.getMessage(), 400));
                    agentRow.put("ok", false);
                    agentRow.put("error", clip(symbolError.getMessage(), 400));
                    failedSymbols.add(note.getSymbol());
                }
                agentTraces.add(agentRow);
                trace.upsertOk(PaperWorkflowStep.AI_DEEP_GATHER,
                        "done=" + index + "/" + tier2.size()
                                + " ok=" + okCount + " failed=" + failedSymbols.size());
                persistProgress(run, trace, agentTraces);
            }

            trace.upsertOk(PaperWorkflowStep.AI_DEEP_GATHER,
                    "symbols=" + tier2.size() + " ok=" + okCount + " failed=" + failedSymbols.size());
            trace.ok(PaperWorkflowStep.APPLY_TIER_UPDATE, "adjusted=" + adjusted);

            ObjectNode stats = objectMapper.createObjectNode();
            stats.put("screened", tier2.size());
            stats.put("adjusted", adjusted);
            stats.put("okCount", okCount);
            stats.put("failedCount", failedSymbols.size());
            ArrayNode failedArr = stats.putArray("failedSymbols");
            for (String s : failedSymbols) {
                failedArr.add(s);
            }
            stats.set("symbols", perSymbol);
            run.setStatsJson(objectMapper.writeValueAsString(stats));
            run.setAdjustedCount(adjusted);
            run.setFinishedAt(LocalDateTime.now());

            if (okCount == 0) {
                run.setStatus(PaperWarehouseAnalystRun.STATUS_FAILED);
                run.setErrorMessage(clip(
                        "票仓分析员全部失败: " + String.join(",", failedSymbols), 1000));
                persistProgress(run, trace, agentTraces);
                throw new IllegalStateException(run.getErrorMessage());
            }

            run.setStatus(PaperWarehouseAnalystRun.STATUS_SUCCEEDED);
            if (!failedSymbols.isEmpty()) {
                run.setErrorMessage(clip(
                        "部分失败(" + failedSymbols.size() + "): " + String.join(",", failedSymbols),
                        1000));
            } else {
                run.setErrorMessage(null);
            }
            trace.ok(PaperWorkflowStep.FINISH_RUN, "adjusted=" + adjusted + " ok=" + okCount);
            persistProgress(run, trace, agentTraces);
        } catch (Exception e) {
            if (!PaperWarehouseAnalystRun.STATUS_FAILED.equals(run.getStatus())
                    && !PaperWarehouseAnalystRun.STATUS_SUCCEEDED.equals(run.getStatus())) {
                run.setStatus(PaperWarehouseAnalystRun.STATUS_FAILED);
                run.setErrorMessage(clip(e.getMessage(), 1000));
                run.setFinishedAt(LocalDateTime.now());
            }
            persistProgress(run, trace, List.of());
            throw e;
        }
    }

    /** 中途落库 framework_trace（含 agents），便于工作流页看到进度。 */
    private void persistProgress(PaperWarehouseAnalystRun run,
                                 PaperWorkflowTrace trace,
                                 List<ObjectNode> agentTraces) {
        run.setFrameworkTrace(mergeTrace(trace, agentTraces));
        runMapper.updateById(run);
    }

    /** 合并 domainSteps 与每票 Agent 轨迹（对齐搜集员 mergeTrace）。 */
    private String mergeTrace(PaperWorkflowTrace domain, List<ObjectNode> agentTraces) {
        try {
            ObjectNode root = (ObjectNode) objectMapper.readTree(domain.toJson(objectMapper));
            ArrayNode agents = root.putArray("agents");
            ObjectNode lastAgent = null;
            if (agentTraces != null) {
                for (ObjectNode row : agentTraces) {
                    if (row == null) {
                        continue;
                    }
                    agents.add(row);
                    if (row.has("trace")) {
                        lastAgent = row;
                    }
                }
            }
            if (lastAgent != null && lastAgent.has("trace")) {
                root.set("agent", lastAgent.get("trace"));
                if (lastAgent.hasNonNull("agentRunId")) {
                    root.put("agentRunId", lastAgent.get("agentRunId").asText());
                }
            }
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            return domain.toJson(objectMapper);
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
                第1步：用 paper-market / paper-research-feed / paper-strategy / web-search 深搜，
                并必须调用 updateValueTier(symbol, tier)：
                - 1：应该买入（须同时：相对低点 relativelyLow=true，且企稳 stabilized=true，
                  fit/evidence 建议≥0.7；缺任一条件只能标 2）；
                - 2：继续观察；3：移出票仓。
                禁止改成 0。等级变更以工具成功为准（工具也会校验低点+企稳并可能把 1 降为 2）。
                第2步：updateValueTier 成功后必须 finish=true，finalOutput 只写短确认 OK；
                禁止重复无增量的 paper_warehouse_analyze 占步。
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
