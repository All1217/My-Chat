package com.mychat.apps.papertrade.warehouse;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mychat.apps.papertrade.research.entity.po.PaperResearchNote;
import com.mychat.apps.papertrade.research.mapper.PaperResearchNoteMapper;
import com.mychat.apps.papertrade.schedule.PaperScheduleService;
import com.mychat.apps.papertrade.schedule.entity.po.PaperScheduleSettings;
import com.mychat.apps.papertrade.trader.entity.vo.PaperAiSubmitVO;
import com.mychat.apps.papertrade.warehouse.entity.po.PaperWarehouseCleanupRun;
import com.mychat.apps.papertrade.warehouse.mapper.PaperWarehouseCleanupRunMapper;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * 票仓清理：跳过 0；1→2；2 滞留≥N→3；3 硬删。无 AI。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaperWarehouseCleanupService {

    public static final String JOB_TYPE = "paper_trade_warehouse_cleanup";

    private final PaperWarehouseCleanupRunMapper runMapper;
    private final PaperResearchNoteMapper noteMapper;
    private final PaperScheduleService scheduleService;
    private final AsyncJobService asyncJobService;
    private final PaperRunLifecycle lifecycle;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public PaperAiSubmitVO submitRun() {
        lifecycle.requireNoActive(runMapper, q -> q.in(
                PaperWarehouseCleanupRun::getStatus,
                PaperRunLifecycle.activeStatuses(
                        PaperWarehouseCleanupRun.STATUS_PENDING,
                        PaperWarehouseCleanupRun.STATUS_RUNNING)));
        PaperWarehouseCleanupRun run = new PaperWarehouseCleanupRun();
        run.setId(UUID.randomUUID().toString().replace("-", ""));
        run.setStatus(PaperWarehouseCleanupRun.STATUS_PENDING);
        runMapper.insert(run);
        AsyncJobVO job = asyncJobService.submit(JOB_TYPE, "票仓清理一轮", run.getId(), null, true);
        run.setJobId(job.getId());
        runMapper.updateById(run);
        PaperAiSubmitVO vo = new PaperAiSubmitVO();
        vo.setRunId(run.getId());
        vo.setJobId(job.getId());
        vo.setStatus(run.getStatus());
        return vo;
    }

    public PaperWarehouseCleanupRun getRun(String id) {
        PaperWarehouseCleanupRun run = runMapper.selectById(id);
        if (run == null) {
            throw new IllegalArgumentException("清理轮次不存在");
        }
        return run;
    }

    public void run(String runId) throws Exception {
        if (!StringUtils.hasText(runId)) {
            throw new IllegalArgumentException("paper_trade_warehouse_cleanup 缺少 refId");
        }
        PaperWarehouseCleanupRun run = runMapper.selectById(runId.trim());
        if (run == null) {
            throw new IllegalArgumentException("清理轮次不存在");
        }
        boolean marked = lifecycle.tryMarkRunning(runMapper, u -> u
                .eq(PaperWarehouseCleanupRun::getId, runId.trim())
                .eq(PaperWarehouseCleanupRun::getStatus, PaperWarehouseCleanupRun.STATUS_PENDING)
                .set(PaperWarehouseCleanupRun::getStatus, PaperWarehouseCleanupRun.STATUS_RUNNING));
        if (!marked) {
            log.info("清理轮次已非 PENDING，跳过 runId={}", runId);
            return;
        }
        PaperWorkflowTrace trace = new PaperWorkflowTrace();
        try {
            trace.ok(PaperWorkflowStep.START_RUN, "jobId=" + run.getJobId());
            PaperScheduleSettings cfg = scheduleService.requireSettings();
            int retainDays = cfg.getWarehouseTier2RetainDays() == null
                    ? 2 : cfg.getWarehouseTier2RetainDays();
            List<PaperResearchNote> notes = noteMapper.selectList(new LambdaQueryWrapper<>());
            trace.ok(PaperWorkflowStep.SCAN_NOTES, "notes=" + notes.size());

            int skipHeld = 0;
            int demote1to2 = 0;
            int skipTier2Young = 0;
            int promote2to3 = 0;
            int deletedTier3 = 0;
            LocalDate today = LocalDate.now();

            for (PaperResearchNote note : notes) {
                int tier = note.getValueTier() == null ? 2 : note.getValueTier();
                if (tier == 0) {
                    skipHeld++;
                    continue;
                }
                if (tier == 1) {
                    note.setValueTier(2);
                    noteMapper.updateById(note);
                    demote1to2++;
                    continue;
                }
                if (tier == 2) {
                    LocalDate created = note.getCreatedAt() == null
                            ? today : note.getCreatedAt().toLocalDate();
                    long days = ChronoUnit.DAYS.between(created, today);
                    if (days < retainDays) {
                        skipTier2Young++;
                        continue;
                    }
                    note.setValueTier(3);
                    note.setKept(false);
                    noteMapper.updateById(note);
                    promote2to3++;
                    continue;
                }
                if (tier == 3) {
                    noteMapper.deleteById(note.getSymbol());
                    deletedTier3++;
                }
            }
            trace.ok(PaperWorkflowStep.APPLY_TIER_RULES,
                    "1→2=" + demote1to2 + " 2→3=" + promote2to3);
            trace.ok(PaperWorkflowStep.DELETE_TIER3, "deleted=" + deletedTier3);

            ObjectNode stats = objectMapper.createObjectNode();
            stats.put("skipHeld", skipHeld);
            stats.put("demote1to2", demote1to2);
            stats.put("skipTier2Young", skipTier2Young);
            stats.put("promote2to3", promote2to3);
            stats.put("deletedTier3", deletedTier3);
            stats.put("retainDays", retainDays);
            run.setStatsJson(objectMapper.writeValueAsString(stats));
            run.setFrameworkTrace(trace.toJson(objectMapper));
            run.setStatus(PaperWarehouseCleanupRun.STATUS_SUCCEEDED);
            run.setFinishedAt(LocalDateTime.now());
            runMapper.updateById(run);
            trace.ok(PaperWorkflowStep.FINISH_RUN, "ok");
        } catch (Exception e) {
            run.setFrameworkTrace(trace.toJson(objectMapper));
            run.setStatus(PaperWarehouseCleanupRun.STATUS_FAILED);
            run.setErrorMessage(clip(e.getMessage(), 1000));
            run.setFinishedAt(LocalDateTime.now());
            runMapper.updateById(run);
            throw e;
        }
    }

    private static String clip(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max);
    }
}
