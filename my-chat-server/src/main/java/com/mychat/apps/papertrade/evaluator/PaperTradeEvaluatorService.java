package com.mychat.apps.papertrade.evaluator;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mychat.apps.papertrade.agent.PaperAgentRuntimeAdapter;
import com.mychat.apps.papertrade.agent.StageEvalBatch;
import com.mychat.apps.papertrade.evaluator.entity.po.PaperTradeEvaluatorRun;
import com.mychat.apps.papertrade.evaluator.mapper.PaperTradeEvaluatorRunMapper;
import com.mychat.apps.papertrade.ledger.PaperTradeService;
import com.mychat.apps.papertrade.mail.PaperMailService;
import com.mychat.apps.papertrade.research.entity.po.PaperResearchNote;
import com.mychat.apps.papertrade.research.entity.po.PaperResearchRun;
import com.mychat.apps.papertrade.research.mapper.PaperResearchNoteMapper;
import com.mychat.apps.papertrade.research.mapper.PaperResearchRunMapper;
import com.mychat.apps.papertrade.risk.PaperStageService;
import com.mychat.apps.papertrade.risk.entity.po.PaperStageSummary;
import com.mychat.apps.papertrade.risk.mapper.PaperStageSummaryMapper;
import com.mychat.apps.papertrade.schedule.PaperScheduleService;
import com.mychat.apps.papertrade.schedule.entity.po.PaperScheduleSettings;
import com.mychat.apps.papertrade.trader.PaperStrategyService;
import com.mychat.apps.papertrade.trader.entity.po.PaperAiDecision;
import com.mychat.apps.papertrade.trader.entity.po.PaperAiRun;
import com.mychat.apps.papertrade.trader.entity.vo.PaperAiSubmitVO;
import com.mychat.apps.papertrade.trader.entity.vo.PaperStrategyVO;
import com.mychat.apps.papertrade.trader.mapper.PaperAiDecisionMapper;
import com.mychat.apps.papertrade.trader.mapper.PaperAiRunMapper;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 交易评估员：周更或枯竭唤醒；写阶段总结、发信；枯竭时再重置。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaperTradeEvaluatorService {

    public static final String JOB_TYPE = "paper_trade_evaluator";

    private final PaperTradeEvaluatorRunMapper runMapper;
    private final AsyncJobService asyncJobService;
    private final PaperRunLifecycle lifecycle;
    private final PaperStrategyService strategyService;
    private final PaperStageService stageService;
    private final PaperStageSummaryMapper stageSummaryMapper;
    private final PaperAiDecisionMapper decisionMapper;
    private final PaperAiRunMapper aiRunMapper;
    private final PaperResearchRunMapper researchRunMapper;
    private final PaperResearchNoteMapper noteMapper;
    private final PaperTradeService tradeService;
    private final PaperScheduleService scheduleService;
    private final PaperAgentRuntimeAdapter agentRuntime;
    private final PaperMailService mailService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 周更入口。 */
    @Transactional
    public PaperAiSubmitVO submitWeekly() {
        return submitRun(PaperTradeEvaluatorRun.TRIGGER_WEEKLY);
    }

    /** 提交一轮；枯竭时若已有活动轮则跳过。 */
    @Transactional
    public PaperAiSubmitVO submitRun(String trigger) {
        String trig = StringUtils.hasText(trigger) ? trigger.trim() : PaperTradeEvaluatorRun.TRIGGER_WEEKLY;
        if (hasActiveRun()) {
            if (PaperTradeEvaluatorRun.TRIGGER_LIQUIDITY_RESET.equals(trig)) {
                log.info("评估员已有活动轮，跳过重复枯竭唤醒");
                PaperAiSubmitVO skip = new PaperAiSubmitVO();
                skip.setStatus("SKIPPED_ACTIVE");
                return skip;
            }
            lifecycle.requireNoActive(runMapper, q -> q.in(
                    PaperTradeEvaluatorRun::getStatus,
                    PaperRunLifecycle.activeStatuses(
                            PaperTradeEvaluatorRun.STATUS_PENDING,
                            PaperTradeEvaluatorRun.STATUS_RUNNING)));
        }
        PaperTradeEvaluatorRun run = new PaperTradeEvaluatorRun();
        run.setId(UUID.randomUUID().toString().replace("-", ""));
        run.setStatus(PaperTradeEvaluatorRun.STATUS_PENDING);
        run.setTriggerType(trig);
        runMapper.insert(run);
        String title = PaperTradeEvaluatorRun.TRIGGER_LIQUIDITY_RESET.equals(trig)
                ? "交易评估员·枯竭总结" : "交易评估员·周复盘";
        AsyncJobVO job = asyncJobService.submit(JOB_TYPE, title, run.getId(), null, true);
        run.setJobId(job.getId());
        runMapper.updateById(run);
        PaperAiSubmitVO vo = new PaperAiSubmitVO();
        vo.setRunId(run.getId());
        vo.setJobId(job.getId());
        vo.setStatus(run.getStatus());
        return vo;
    }

    public PaperTradeEvaluatorRun getRun(String id) {
        PaperTradeEvaluatorRun run = runMapper.selectById(id);
        if (run == null) {
            throw new IllegalArgumentException("评估员轮次不存在");
        }
        return run;
    }

    public void run(String runId) throws Exception {
        if (!StringUtils.hasText(runId)) {
            throw new IllegalArgumentException("paper_trade_evaluator 缺少 refId");
        }
        PaperTradeEvaluatorRun run = runMapper.selectById(runId.trim());
        if (run == null) {
            throw new IllegalArgumentException("评估员轮次不存在");
        }
        boolean marked = lifecycle.tryMarkRunning(runMapper, u -> u
                .eq(PaperTradeEvaluatorRun::getId, runId.trim())
                .eq(PaperTradeEvaluatorRun::getStatus, PaperTradeEvaluatorRun.STATUS_PENDING)
                .set(PaperTradeEvaluatorRun::getStatus, PaperTradeEvaluatorRun.STATUS_RUNNING));
        if (!marked) {
            log.info("评估员轮次已非 PENDING，跳过 runId={}", runId);
            return;
        }
        PaperWorkflowTrace trace = new PaperWorkflowTrace();
        try {
            trace.ok(PaperWorkflowStep.START_RUN, "trigger=" + run.getTriggerType());
            BigDecimal endEquity = tradeService.markEquity().equity();
            String contextJson = buildContext(run.getTriggerType(), endEquity);
            trace.ok(PaperWorkflowStep.GATHER_EVIDENCE, "contextBytes=" + contextJson.length());

            PaperAgentRuntimeAdapter.Outcome outcome =
                    agentRuntime.evaluate(prompt(run.getTriggerType(), contextJson),
                            run.getId(), run.getJobId());
            trace.bindAgentRun(outcome.agentRunId());
            StageEvalBatch batch = parseBatch(outcome.answer(), outcome.observations());
            trace.ok(PaperWorkflowStep.AI_EVALUATE, "agentRunId=" + outcome.agentRunId());

            String summaryText = StringUtils.hasText(batch.summaryText())
                    ? batch.summaryText() : fallbackSummary(endEquity);
            PaperStageSummary stage = stageService.insertSummary(endEquity, summaryText);
            run.setStageSummaryId(stage.getId());
            trace.ok(PaperWorkflowStep.WRITE_STAGE_SUMMARY, "stageId=" + stage.getId());

            boolean reset = PaperTradeEvaluatorRun.TRIGGER_LIQUIDITY_RESET.equals(run.getTriggerType());
            if (reset) {
                stageService.resetAccountOnly();
            }

            PaperScheduleSettings sch = scheduleService.requireSettings();
            String to = sch.getEvaluatorEmail();
            String emailBody = buildEmailBody(batch, summaryText);
            String emailStatus = mailService.send(
                    to,
                    "模拟炒股评估 · " + run.getTriggerType(),
                    emailBody);
            run.setEmailStatus(emailStatus);
            trace.ok(PaperWorkflowStep.SEND_EMAIL, "status=" + emailStatus);
            trace.ok(PaperWorkflowStep.FINISH_RUN, reset ? "resetAccount" : "ok");

            ObjectNode stats = objectMapper.createObjectNode();
            stats.put("trigger", run.getTriggerType());
            stats.put("stageSummaryId", stage.getId());
            stats.put("emailStatus", emailStatus);
            stats.put("resetAccount", reset);
            if (StringUtils.hasText(batch.strategyAdvice())) {
                stats.put("strategyAdvice", clip(batch.strategyAdvice(), 2000));
            }
            run.setStatsJson(objectMapper.writeValueAsString(stats));
            run.setFrameworkTrace(mergeTrace(trace, outcome.traceJson()));
            run.setStatus(PaperTradeEvaluatorRun.STATUS_SUCCEEDED);
            run.setFinishedAt(LocalDateTime.now());
            runMapper.updateById(run);
        } catch (Exception e) {
            run.setFrameworkTrace(trace.toJson(objectMapper));
            run.setStatus(PaperTradeEvaluatorRun.STATUS_FAILED);
            run.setErrorMessage(clip(e.getMessage(), 1000));
            run.setFinishedAt(LocalDateTime.now());
            runMapper.updateById(run);
            throw e;
        }
    }

    private boolean hasActiveRun() {
        Long n = runMapper.selectCount(new LambdaQueryWrapper<PaperTradeEvaluatorRun>()
                .in(PaperTradeEvaluatorRun::getStatus,
                        PaperTradeEvaluatorRun.STATUS_PENDING,
                        PaperTradeEvaluatorRun.STATUS_RUNNING));
        return n != null && n > 0;
    }

    private String buildContext(String trigger, BigDecimal endEquity) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("trigger", trigger);
        root.put("endEquity", endEquity == null ? 0 : endEquity.doubleValue());
        PaperStrategyVO strategy = strategyService.get();
        root.put("customContent", strategy.getCustomContent() == null ? "" : strategy.getCustomContent());
        root.put("lockedContentClip", clip(strategy.getLockedContent(), 2500));
        root.put("lastStageSummary", nullToEmpty(stageService.latestText()));

        ArrayNode decisions = root.putArray("recentDecisions");
        List<PaperAiDecision> decRows = decisionMapper.selectList(new LambdaQueryWrapper<PaperAiDecision>()
                .orderByDesc(PaperAiDecision::getCreatedAt)
                .last("LIMIT 30"));
        for (PaperAiDecision d : decRows) {
            ObjectNode n = decisions.addObject();
            n.put("action", d.getAction());
            n.put("symbol", d.getSymbol());
            n.put("execStatus", d.getExecStatus());
            n.put("skipReason", d.getSkipReason() == null ? "" : d.getSkipReason());
        }

        ArrayNode aiRuns = root.putArray("recentAiRuns");
        for (PaperAiRun r : aiRunMapper.selectList(new LambdaQueryWrapper<PaperAiRun>()
                .orderByDesc(PaperAiRun::getCreatedAt).last("LIMIT 8"))) {
            ObjectNode n = aiRuns.addObject();
            n.put("id", r.getId());
            n.put("status", r.getStatus());
        }

        ArrayNode researchRuns = root.putArray("recentResearchRuns");
        for (PaperResearchRun r : researchRunMapper.selectList(new LambdaQueryWrapper<PaperResearchRun>()
                .orderByDesc(PaperResearchRun::getCreatedAt).last("LIMIT 8"))) {
            ObjectNode n = researchRuns.addObject();
            n.put("id", r.getId());
            n.put("status", r.getStatus());
            n.put("keptCount", r.getKeptCount() == null ? 0 : r.getKeptCount());
        }

        ArrayNode notes = root.putArray("researchNotes");
        for (PaperResearchNote note : noteMapper.selectList(new LambdaQueryWrapper<PaperResearchNote>()
                .eq(PaperResearchNote::getKept, true)
                .orderByDesc(PaperResearchNote::getUpdatedAt)
                .last("LIMIT 20"))) {
            ObjectNode n = notes.addObject();
            n.put("symbol", note.getSymbol());
            n.put("valueTier", note.getValueTier() == null ? 2 : note.getValueTier());
            n.put("story", clip(note.getStory(), 120));
        }

        ObjectNode account = root.putObject("account");
        var acc = tradeService.requireAccount();
        account.put("cash", acc.getCash() == null ? 0 : acc.getCash().doubleValue());
        account.put("initialCash", acc.getInitialCash() == null ? 0 : acc.getInitialCash().doubleValue());
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
    }

    private String prompt(String trigger, String contextJson) {
        return """
                你是模拟炒股的交易评估员。trigger=%s。
                结合上下文复盘本阶段表现，输出 StageEvalBatch JSON（在 paper_trade_evaluate 观察中）。
                summaryText：阶段总结正文（可含指标解读与主要亏损来源）。
                strategyAdvice：仅针对「可编辑风格与偏好」的修改建议；禁止建议改锁定风控、value_tier、整手、平台硬约束。
                emailBody：给用户的邮件正文，含建议要点。
                可按需调用只读工具补行情/爬虫证据，非强制。
                【上下文】
                %s
                """.formatted(trigger, contextJson);
    }

    private StageEvalBatch parseBatch(String answer, String observations) {
        for (String cand : List.of(answer, observations)) {
            if (!StringUtils.hasText(cand)) {
                continue;
            }
            try {
                String json = extractJsonObject(cand);
                if (json == null) {
                    continue;
                }
                StageEvalBatch batch = objectMapper.readValue(json, StageEvalBatch.class);
                if (batch != null && StringUtils.hasText(batch.summaryText())) {
                    return batch;
                }
            } catch (Exception ignored) {
                // try next
            }
        }
        return new StageEvalBatch("（评估员未产出结构化摘要，使用兜底模板）", "", "", List.of());
    }

    private static String extractJsonObject(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        return text.substring(start, end + 1);
    }

    private String fallbackSummary(BigDecimal endEquity) {
        return "阶段结束权益 " + (endEquity == null ? "?" : endEquity.toPlainString())
                + "。评估员结构化输出缺失，请人工复盘。";
    }

    private String buildEmailBody(StageEvalBatch batch, String summaryText) {
        if (StringUtils.hasText(batch.emailBody())) {
            return batch.emailBody().trim();
        }
        StringBuilder sb = new StringBuilder();
        sb.append("【阶段要点】\n").append(summaryText).append("\n\n");
        if (StringUtils.hasText(batch.strategyAdvice())) {
            sb.append("【可编辑策略修改建议】\n").append(batch.strategyAdvice().trim());
        } else {
            sb.append("【可编辑策略修改建议】\n（本期无额外建议）");
        }
        return sb.toString();
    }

    private String mergeTrace(PaperWorkflowTrace domain, String agentTraceJson) {
        try {
            var root = objectMapper.readTree(domain.toJson(objectMapper));
            if (agentTraceJson != null && !agentTraceJson.isBlank()) {
                ((ObjectNode) root).set("agent", objectMapper.readTree(agentTraceJson));
            }
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            return domain.toJson(objectMapper);
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String clip(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max);
    }
}
