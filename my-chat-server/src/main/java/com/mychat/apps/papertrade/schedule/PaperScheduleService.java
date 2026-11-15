package com.mychat.apps.papertrade.schedule;

import com.mychat.apps.papertrade.PaperTradeProperties;
import com.mychat.apps.papertrade.schedule.entity.dto.PaperScheduleRequest;
import com.mychat.apps.papertrade.schedule.entity.po.PaperScheduleSettings;
import com.mychat.apps.papertrade.schedule.entity.vo.PaperScheduleVO;
import com.mychat.apps.papertrade.schedule.mapper.PaperScheduleSettingsMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** 定时配置读写与校验；运行时以 DB 为准。 */
@Service
@RequiredArgsConstructor
public class PaperScheduleService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PaperScheduleSettingsMapper mapper;
    private final PaperTradeProperties properties;

    /** 读取单行配置；不存在则按 yaml 种子创建。 */
    @Transactional
    public PaperScheduleSettings requireSettings() {
        PaperScheduleSettings row = mapper.selectById(PaperScheduleSettings.DEFAULT_ID);
        if (row != null) {
            return row;
        }
        return seedIfAbsent();
    }

    /** 空表时写入默认行。 */
    @Transactional
    public PaperScheduleSettings seedIfAbsent() {
        PaperScheduleSettings existing = mapper.selectById(PaperScheduleSettings.DEFAULT_ID);
        if (existing != null) {
            return existing;
        }
        PaperScheduleSettings seed = new PaperScheduleSettings();
        seed.setId(PaperScheduleSettings.DEFAULT_ID);
        seed.setResearchEnabled(Boolean.TRUE.equals(properties.isResearchScheduleEnabled()));
        seed.setResearchCron(blankTo(
                properties.getResearchScheduleCron(), "0 0 9 * * *"));
        seed.setTraderEnabled(Boolean.TRUE.equals(properties.isScheduleEnabled()));
        seed.setTraderCron(blankTo(properties.getScheduleCron(), "0 35 15 * * MON-FRI"));
        seed.setZoneId(blankTo(properties.getScheduleZone(), "Asia/Shanghai"));
        seed.setIncludeWatchlist(true);
        seed.setResearchToolSearchEnabled(false);
        seed.setUpdatedAt(LocalDateTime.now());
        validateCron(seed.getResearchCron());
        validateCron(seed.getTraderCron());
        validateZone(seed.getZoneId());
        mapper.insert(seed);
        return seed;
    }

    /** 视图对象。 */
    public PaperScheduleVO getVo() {
        return toVo(requireSettings());
    }

    /** 更新并返回；调用方负责 reschedule。 */
    @Transactional
    public PaperScheduleSettings update(PaperScheduleRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        PaperScheduleSettings row = requireSettings();
        if (req.getResearchEnabled() != null) {
            row.setResearchEnabled(req.getResearchEnabled());
        }
        if (req.getTraderEnabled() != null) {
            row.setTraderEnabled(req.getTraderEnabled());
        }
        if (req.getResearchCron() != null) {
            String cron = req.getResearchCron().trim();
            validateCron(cron);
            row.setResearchCron(cron);
        }
        if (req.getTraderCron() != null) {
            String cron = req.getTraderCron().trim();
            validateCron(cron);
            row.setTraderCron(cron);
        }
        if (req.getZoneId() != null) {
            String zone = req.getZoneId().trim();
            validateZone(zone);
            row.setZoneId(zone);
        }
        if (req.getIncludeWatchlist() != null) {
            row.setIncludeWatchlist(req.getIncludeWatchlist());
        }
        if (req.getResearchToolSearchEnabled() != null) {
            row.setResearchToolSearchEnabled(req.getResearchToolSearchEnabled());
        }
        row.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(row);
        return row;
    }

    /** Spring 6 域 cron 校验。 */
    public static void validateCron(String cron) {
        if (!StringUtils.hasText(cron)) {
            throw new IllegalArgumentException("cron 不能为空");
        }
        try {
            CronExpression.parse(cron.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("无效的 cron 表达式: " + cron);
        }
    }

    /** 时区校验。 */
    public static void validateZone(String zoneId) {
        if (!StringUtils.hasText(zoneId)) {
            throw new IllegalArgumentException("时区不能为空");
        }
        try {
            ZoneId.of(zoneId.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("无效的时区: " + zoneId);
        }
    }

    public PaperScheduleVO toVo(PaperScheduleSettings row) {
        PaperScheduleVO vo = new PaperScheduleVO();
        vo.setResearchEnabled(Boolean.TRUE.equals(row.getResearchEnabled()));
        vo.setResearchCron(row.getResearchCron());
        vo.setTraderEnabled(Boolean.TRUE.equals(row.getTraderEnabled()));
        vo.setTraderCron(row.getTraderCron());
        vo.setZoneId(row.getZoneId());
        // 缺省纳入自选（旧行迁移前兼容）
        vo.setIncludeWatchlist(row.getIncludeWatchlist() == null || Boolean.TRUE.equals(row.getIncludeWatchlist()));
        vo.setResearchToolSearchEnabled(Boolean.TRUE.equals(row.getResearchToolSearchEnabled()));
        if (row.getUpdatedAt() != null) {
            vo.setUpdatedAt(row.getUpdatedAt().format(TS));
        }
        return vo;
    }

    private static String blankTo(String v, String fallback) {
        return StringUtils.hasText(v) ? v.trim() : fallback;
    }
}
