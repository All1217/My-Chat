package com.mychat.apps.papertrade.schedule;

import com.mychat.apps.papertrade.evaluator.PaperTradeEvaluatorService;
import com.mychat.apps.papertrade.research.PaperResearchService;
import com.mychat.apps.papertrade.schedule.entity.po.PaperScheduleSettings;
import com.mychat.apps.papertrade.trader.PaperAiTraderService;
import com.mychat.apps.papertrade.warehouse.PaperWarehouseAnalystService;
import com.mychat.apps.papertrade.warehouse.PaperWarehouseCleanupService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 按 DB 配置动态挂载搜集员 / 交易员 / 票仓分析员 / 票仓清理 / 交易评估员 cron。
 */
@Slf4j
@Component
@Order(40)
@RequiredArgsConstructor
public class PaperScheduleManager implements ApplicationRunner {

    private final PaperScheduleService scheduleService;
    private final PaperResearchService researchService;
    private final PaperAiTraderService traderService;
    private final PaperWarehouseCleanupService cleanupService;
    private final PaperWarehouseAnalystService analystService;
    private final PaperTradeEvaluatorService evaluatorService;
    private final ThreadPoolTaskScheduler paperTradeTaskScheduler;

    private final AtomicReference<ScheduledFuture<?>> researchFuture = new AtomicReference<>();
    private final AtomicReference<ScheduledFuture<?>> traderFuture = new AtomicReference<>();
    private final AtomicReference<ScheduledFuture<?>> cleanupFuture = new AtomicReference<>();
    private final AtomicReference<ScheduledFuture<?>> analystFuture = new AtomicReference<>();
    private final AtomicReference<ScheduledFuture<?>> evaluatorFuture = new AtomicReference<>();

    @Override
    public void run(ApplicationArguments args) {
        try {
            scheduleService.seedIfAbsent();
            reschedule();
        } catch (Exception e) {
            log.warn("模拟炒股定时初始化跳过: {}", e.getMessage());
        }
    }

    /** 取消旧任务并按当前配置重挂。 */
    public synchronized void reschedule() {
        PaperScheduleSettings cfg = scheduleService.requireSettings();
        ZoneId zone = ZoneId.of(cfg.getZoneId());
        cancel(researchFuture);
        cancel(traderFuture);
        cancel(cleanupFuture);
        cancel(analystFuture);
        cancel(evaluatorFuture);
        if (Boolean.TRUE.equals(cfg.getResearchEnabled())) {
            ScheduledFuture<?> f = paperTradeTaskScheduler.schedule(
                    this::runResearch,
                    new CronTrigger(cfg.getResearchCron(), zone));
            researchFuture.set(f);
            log.info("搜集员定时已挂载 cron={} zone={}", cfg.getResearchCron(), cfg.getZoneId());
        } else {
            log.info("搜集员定时已关闭");
        }
        if (Boolean.TRUE.equals(cfg.getTraderEnabled())) {
            ScheduledFuture<?> f = paperTradeTaskScheduler.schedule(
                    this::runTrader,
                    new CronTrigger(cfg.getTraderCron(), zone));
            traderFuture.set(f);
            log.info("交易员定时已挂载 cron={} zone={}", cfg.getTraderCron(), cfg.getZoneId());
        } else {
            log.info("交易员定时已关闭");
        }
        if (cfg.getAnalystEnabled() == null || Boolean.TRUE.equals(cfg.getAnalystEnabled())) {
            String cron = cfg.getAnalystCron() == null || cfg.getAnalystCron().isBlank()
                    ? "0 0 18 * * *" : cfg.getAnalystCron();
            ScheduledFuture<?> f = paperTradeTaskScheduler.schedule(
                    this::runAnalyst,
                    new CronTrigger(cron, zone));
            analystFuture.set(f);
            log.info("票仓分析员定时已挂载 cron={} zone={}", cron, cfg.getZoneId());
        } else {
            log.info("票仓分析员定时已关闭");
        }
        if (cfg.getCleanupEnabled() == null || Boolean.TRUE.equals(cfg.getCleanupEnabled())) {
            String cron = cfg.getCleanupCron() == null || cfg.getCleanupCron().isBlank()
                    ? "0 0 23 * * *" : cfg.getCleanupCron();
            ScheduledFuture<?> f = paperTradeTaskScheduler.schedule(
                    this::runCleanup,
                    new CronTrigger(cron, zone));
            cleanupFuture.set(f);
            log.info("票仓清理定时已挂载 cron={} zone={}", cron, cfg.getZoneId());
        } else {
            log.info("票仓清理定时已关闭");
        }
        if (cfg.getEvaluatorEnabled() == null || Boolean.TRUE.equals(cfg.getEvaluatorEnabled())) {
            String cron = cfg.getEvaluatorCron() == null || cfg.getEvaluatorCron().isBlank()
                    ? PaperScheduleService.DEFAULT_EVALUATOR_CRON : cfg.getEvaluatorCron();
            ScheduledFuture<?> f = paperTradeTaskScheduler.schedule(
                    this::runEvaluator,
                    new CronTrigger(cron, zone));
            evaluatorFuture.set(f);
            log.info("交易评估员定时已挂载 cron={} zone={}", cron, cfg.getZoneId());
        } else {
            log.info("交易评估员定时已关闭");
        }
    }

    void runResearch() {
        try {
            log.info("定时触发 paper_trade_research");
            researchService.submitRun();
        } catch (Exception e) {
            log.warn("搜集轮提交失败: {}", e.getMessage());
        }
    }

    void runTrader() {
        try {
            warnIfIntraday();
            log.info("定时触发 paper_trade_ai_round");
            traderService.submitRun();
        } catch (Exception e) {
            log.warn("盘后 AI 轮提交失败: {}", e.getMessage());
        }
    }

    void runAnalyst() {
        try {
            log.info("定时触发 paper_trade_warehouse_analyst");
            analystService.submitRun();
        } catch (Exception e) {
            log.warn("票仓分析员提交失败: {}", e.getMessage());
        }
    }

    void runCleanup() {
        try {
            log.info("定时触发 paper_trade_warehouse_cleanup");
            cleanupService.submitRun();
        } catch (Exception e) {
            log.warn("票仓清理提交失败: {}", e.getMessage());
        }
    }

    void runEvaluator() {
        try {
            log.info("定时触发 paper_trade_evaluator");
            evaluatorService.submitWeekly();
        } catch (Exception e) {
            log.warn("交易评估员提交失败: {}", e.getMessage());
        }
    }

    private void warnIfIntraday() {
        try {
            PaperScheduleSettings cfg = scheduleService.requireSettings();
            ZonedDateTime now = ZonedDateTime.now(ZoneId.of(cfg.getZoneId()));
            DayOfWeek d = now.getDayOfWeek();
            if (d == DayOfWeek.SATURDAY || d == DayOfWeek.SUNDAY) {
                return;
            }
            LocalTime t = now.toLocalTime();
            if (!t.isBefore(LocalTime.of(9, 0)) && t.isBefore(LocalTime.of(15, 0))) {
                log.warn("交易员定时落在工作日 09:00–15:00，当前撮合为收盘价，建议 cron ≥15:05");
            }
        } catch (Exception ignored) {
            // 告警失败不影响执行
        }
    }

    private static void cancel(AtomicReference<ScheduledFuture<?>> ref) {
        ScheduledFuture<?> f = ref.getAndSet(null);
        if (f != null) {
            f.cancel(false);
        }
    }

    @PreDestroy
    public void shutdown() {
        cancel(researchFuture);
        cancel(traderFuture);
        cancel(cleanupFuture);
        cancel(analystFuture);
        cancel(evaluatorFuture);
    }
}
