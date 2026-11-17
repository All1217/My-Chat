package com.mychat.apps.papertrade.risk;

import com.mychat.apps.papertrade.PaperTradeProperties;
import com.mychat.apps.papertrade.risk.entity.dto.PaperRiskSettingsRequest;
import com.mychat.apps.papertrade.risk.entity.po.PaperRiskSettings;
import com.mychat.apps.papertrade.risk.entity.vo.PaperRiskSettingsVO;
import com.mychat.apps.papertrade.risk.mapper.PaperRiskSettingsMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** 运行时风控读写；YAML 仅作空表种子。 */
@Service
@RequiredArgsConstructor
public class PaperRiskSettingsService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PaperRiskSettingsMapper mapper;
    private final PaperTradeProperties properties;

    /** 读取单行；不存在则按 YAML 种子。 */
    @Transactional
    public PaperRiskSettings requireSettings() {
        PaperRiskSettings row = mapper.selectById(PaperRiskSettings.DEFAULT_ID);
        if (row != null) {
            return row;
        }
        return seedIfAbsent();
    }

    /** 空表时写入默认行。 */
    @Transactional
    public PaperRiskSettings seedIfAbsent() {
        PaperRiskSettings existing = mapper.selectById(PaperRiskSettings.DEFAULT_ID);
        if (existing != null) {
            return existing;
        }
        PaperRiskSettings seed = new PaperRiskSettings();
        seed.setId(PaperRiskSettings.DEFAULT_ID);
        seed.setMaxPositions(Math.max(1, properties.getMaxPositions()));
        seed.setMaxSingleAllocation(positive(properties.getMaxSingleAllocation(), 0.95));
        seed.setRiskBudgetPct(positive(properties.getRiskBudgetPct(), 0.02));
        seed.setRiskBudgetTightPct(positive(properties.getRiskBudgetTightPct(), 0.01));
        seed.setDrawdownRestrictPct(positive(properties.getDrawdownRestrictPct(), 0.08));
        seed.setLossStreakLimit(Math.max(1, properties.getLossStreakLimit()));
        seed.setBuyPauseDays(Math.max(0, properties.getBuyPauseDays()));
        seed.setReviewConfidenceThreshold(positive(properties.getReviewConfidenceThreshold(), 0.55));
        seed.setAutoExecute(properties.isAutoExecute());
        seed.setCooldownDaysStock(2);
        seed.setCooldownDaysEtf(1);
        seed.setStockTakeProfitPct(0.04);
        seed.setStockStopLossPct(0.03);
        seed.setStockMaxHoldDays(10);
        seed.setEtfTakeProfitPct(0.03);
        seed.setEtfStopLossPct(0.02);
        seed.setEtfMaxHoldDays(15);
        seed.setQdiiTakeProfitPct(0.045);
        seed.setQdiiStopLossPct(0.03);
        seed.setQdiiMaxHoldDays(10);
        seed.setUpdatedAt(LocalDateTime.now());
        mapper.insert(seed);
        return seed;
    }

    public PaperRiskSettingsVO getVo() {
        return toVo(requireSettings());
    }

    /** 部分更新并校验范围。 */
    @Transactional
    public PaperRiskSettings update(PaperRiskSettingsRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("风控请求不能为空");
        }
        PaperRiskSettings row = requireSettings();
        if (req.getMaxPositions() != null) {
            row.setMaxPositions(rangeInt(req.getMaxPositions(), 1, 20, "最多持仓"));
        }
        if (req.getMaxSingleAllocation() != null) {
            row.setMaxSingleAllocation(rangePct(req.getMaxSingleAllocation(), 0.05, 1.0, "单票上限"));
        }
        if (req.getRiskBudgetPct() != null) {
            row.setRiskBudgetPct(rangePct(req.getRiskBudgetPct(), 0.001, 0.2, "风险预算"));
        }
        if (req.getRiskBudgetTightPct() != null) {
            row.setRiskBudgetTightPct(rangePct(req.getRiskBudgetTightPct(), 0.001, 0.2, "收紧预算"));
        }
        if (req.getDrawdownRestrictPct() != null) {
            row.setDrawdownRestrictPct(rangePct(req.getDrawdownRestrictPct(), 0.01, 0.5, "回撤熔断"));
        }
        if (req.getLossStreakLimit() != null) {
            row.setLossStreakLimit(rangeInt(req.getLossStreakLimit(), 1, 20, "连亏笔数"));
        }
        if (req.getBuyPauseDays() != null) {
            row.setBuyPauseDays(rangeInt(req.getBuyPauseDays(), 0, 30, "暂停天数"));
        }
        if (req.getReviewConfidenceThreshold() != null) {
            row.setReviewConfidenceThreshold(
                    rangePct(req.getReviewConfidenceThreshold(), 0.0, 1.0, "置信度阈值"));
        }
        if (req.getAutoExecute() != null) {
            row.setAutoExecute(req.getAutoExecute());
        }
        if (req.getCooldownDaysStock() != null) {
            row.setCooldownDaysStock(rangeInt(req.getCooldownDaysStock(), 0, 30, "股票冷却"));
        }
        if (req.getCooldownDaysEtf() != null) {
            row.setCooldownDaysEtf(rangeInt(req.getCooldownDaysEtf(), 0, 30, "ETF冷却"));
        }
        if (req.getStockTakeProfitPct() != null) {
            row.setStockTakeProfitPct(rangePct(req.getStockTakeProfitPct(), 0.005, 0.5, "股止盈"));
        }
        if (req.getStockStopLossPct() != null) {
            row.setStockStopLossPct(rangePct(req.getStockStopLossPct(), 0.005, 0.5, "股止损"));
        }
        if (req.getStockMaxHoldDays() != null) {
            row.setStockMaxHoldDays(rangeInt(req.getStockMaxHoldDays(), 1, 120, "股持有天数"));
        }
        if (req.getEtfTakeProfitPct() != null) {
            row.setEtfTakeProfitPct(rangePct(req.getEtfTakeProfitPct(), 0.005, 0.5, "ETF止盈"));
        }
        if (req.getEtfStopLossPct() != null) {
            row.setEtfStopLossPct(rangePct(req.getEtfStopLossPct(), 0.005, 0.5, "ETF止损"));
        }
        if (req.getEtfMaxHoldDays() != null) {
            row.setEtfMaxHoldDays(rangeInt(req.getEtfMaxHoldDays(), 1, 120, "ETF持有天数"));
        }
        if (req.getQdiiTakeProfitPct() != null) {
            row.setQdiiTakeProfitPct(rangePct(req.getQdiiTakeProfitPct(), 0.005, 0.5, "QDII止盈"));
        }
        if (req.getQdiiStopLossPct() != null) {
            row.setQdiiStopLossPct(rangePct(req.getQdiiStopLossPct(), 0.005, 0.5, "QDII止损"));
        }
        if (req.getQdiiMaxHoldDays() != null) {
            row.setQdiiMaxHoldDays(rangeInt(req.getQdiiMaxHoldDays(), 1, 120, "QDII持有天数"));
        }
        row.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(row);
        return row;
    }

    public PaperRiskSettingsVO toVo(PaperRiskSettings row) {
        PaperRiskSettingsVO vo = new PaperRiskSettingsVO();
        vo.setMaxPositions(n(row.getMaxPositions(), 2));
        vo.setMaxSingleAllocation(n(row.getMaxSingleAllocation(), 0.95));
        vo.setRiskBudgetPct(n(row.getRiskBudgetPct(), 0.02));
        vo.setRiskBudgetTightPct(n(row.getRiskBudgetTightPct(), 0.01));
        vo.setDrawdownRestrictPct(n(row.getDrawdownRestrictPct(), 0.08));
        vo.setLossStreakLimit(n(row.getLossStreakLimit(), 3));
        vo.setBuyPauseDays(n(row.getBuyPauseDays(), 2));
        vo.setReviewConfidenceThreshold(n(row.getReviewConfidenceThreshold(), 0.55));
        vo.setAutoExecute(row.getAutoExecute() == null || Boolean.TRUE.equals(row.getAutoExecute()));
        vo.setCooldownDaysStock(n(row.getCooldownDaysStock(), 2));
        vo.setCooldownDaysEtf(n(row.getCooldownDaysEtf(), 1));
        vo.setStockTakeProfitPct(n(row.getStockTakeProfitPct(), 0.04));
        vo.setStockStopLossPct(n(row.getStockStopLossPct(), 0.03));
        vo.setStockMaxHoldDays(n(row.getStockMaxHoldDays(), 10));
        vo.setEtfTakeProfitPct(n(row.getEtfTakeProfitPct(), 0.03));
        vo.setEtfStopLossPct(n(row.getEtfStopLossPct(), 0.02));
        vo.setEtfMaxHoldDays(n(row.getEtfMaxHoldDays(), 15));
        vo.setQdiiTakeProfitPct(n(row.getQdiiTakeProfitPct(), 0.045));
        vo.setQdiiStopLossPct(n(row.getQdiiStopLossPct(), 0.03));
        vo.setQdiiMaxHoldDays(n(row.getQdiiMaxHoldDays(), 10));
        if (row.getUpdatedAt() != null) {
            vo.setUpdatedAt(row.getUpdatedAt().format(TS));
        }
        return vo;
    }

    private static double positive(double v, double fallback) {
        return v <= 0 ? fallback : v;
    }

    private static int n(Integer v, int fallback) {
        return v == null || v < 0 ? fallback : v;
    }

    private static double n(Double v, double fallback) {
        return v == null || v < 0 ? fallback : v;
    }

    private static int rangeInt(int v, int min, int max, String label) {
        if (v < min || v > max) {
            throw new IllegalArgumentException(label + "须在 " + min + "–" + max);
        }
        return v;
    }

    private static double rangePct(double v, double min, double max, String label) {
        if (v < min || v > max) {
            throw new IllegalArgumentException(label + "须在 " + min + "–" + max);
        }
        return v;
    }
}
