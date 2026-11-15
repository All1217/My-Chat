package com.mychat.apps.papertrade.schedule;

import com.mychat.apps.papertrade.research.PaperResearchService;
import com.mychat.apps.papertrade.schedule.entity.po.PaperScheduleSettings;
import com.mychat.apps.papertrade.trader.PaperAiTraderService;
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
 * 按 DB 配置动态挂载搜集员 / 交易员 cron（不引入 Quartz）。
 */
@Slf4j
@Component
@Order(40)
@RequiredArgsConstructor
public class PaperScheduleManager implements ApplicationRunner {

    private final PaperScheduleService scheduleService;
    private final PaperResearchService researchService;
    private final PaperAiTraderService traderService;
    private final ThreadPoolTaskScheduler paperTradeTaskScheduler;

    private final AtomicReference<ScheduledFuture<?>> researchFuture = new AtomicReference<>();
    private final AtomicReference<ScheduledFuture<?>> traderFuture = new AtomicReference<>();

    /** 启动后 seed + 挂载。 */
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
    }

    /** 搜集一轮。 */
    void runResearch() {
        try {
            log.info("定时触发 paper_trade_research");
            researchService.submitRun();
        } catch (Exception e) {
            log.warn("搜集轮提交失败: {}", e.getMessage());
        }
    }

    /** 交易员盘后一轮；若落在工作日盘中仅告警仍执行。 */
    void runTrader() {
        try {
            warnIfIntraday();
            log.info("定时触发 paper_trade_ai_round");
            traderService.submitRun();
        } catch (Exception e) {
            log.warn("盘后 AI 轮提交失败: {}", e.getMessage());
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
    }
}
