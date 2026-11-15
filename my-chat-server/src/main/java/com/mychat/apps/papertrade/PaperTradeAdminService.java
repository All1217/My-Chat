package com.mychat.apps.papertrade;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mychat.apps.papertrade.ledger.PaperTradeService;
import com.mychat.apps.papertrade.ledger.entity.vo.PaperAccountVO;
import com.mychat.apps.papertrade.research.entity.dto.PaperResearchNoteRequest;
import com.mychat.apps.papertrade.research.entity.po.PaperResearchNote;
import com.mychat.apps.papertrade.research.entity.po.PaperResearchRun;
import com.mychat.apps.papertrade.research.entity.vo.PaperResearchNoteAdminVO;
import com.mychat.apps.papertrade.research.mapper.PaperResearchNoteMapper;
import com.mychat.apps.papertrade.research.mapper.PaperResearchRunMapper;
import com.mychat.apps.papertrade.risk.entity.dto.PaperStageSummaryRequest;
import com.mychat.apps.papertrade.risk.entity.po.PaperStageSummary;
import com.mychat.apps.papertrade.risk.mapper.PaperStageSummaryMapper;
import com.mychat.apps.papertrade.trader.entity.dto.PaperAiDecisionUpdateRequest;
import com.mychat.apps.papertrade.trader.entity.po.PaperAiDecision;
import com.mychat.apps.papertrade.trader.entity.po.PaperAiRun;
import com.mychat.apps.papertrade.trader.entity.vo.PaperAiDecisionVO;
import com.mychat.apps.papertrade.trader.mapper.PaperAiDecisionMapper;
import com.mychat.apps.papertrade.trader.mapper.PaperAiRunMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 模拟炒股管理端：历史表 CRUD、注销阶段训练。 */
@Service
@RequiredArgsConstructor
public class PaperTradeAdminService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private final PaperTradeService tradeService;
    private final PaperStageSummaryMapper stageSummaryMapper;
    private final PaperAiDecisionMapper decisionMapper;
    private final PaperAiRunMapper aiRunMapper;
    private final PaperResearchRunMapper researchRunMapper;
    private final PaperResearchNoteMapper researchNoteMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 清空业务数据并重置账户，保留策略与账户行。 */
    @Transactional
    public PaperAccountVO wipeTraining() {
        // 1. 清历史业务表（策略 paper_strategy 不动）
        decisionMapper.delete(new LambdaQueryWrapper<PaperAiDecision>().isNotNull(PaperAiDecision::getId));
        aiRunMapper.delete(new LambdaQueryWrapper<PaperAiRun>().isNotNull(PaperAiRun::getId));
        researchNoteMapper.delete(new LambdaQueryWrapper<PaperResearchNote>().isNotNull(PaperResearchNote::getSymbol));
        researchRunMapper.delete(new LambdaQueryWrapper<PaperResearchRun>().isNotNull(PaperResearchRun::getId));
        stageSummaryMapper.delete(new LambdaQueryWrapper<PaperStageSummary>().isNotNull(PaperStageSummary::getId));
        // 2. 清持仓/订单/冷却并复位资金
        return tradeService.resetAccount();
    }

    // —— 阶段总结 ——

    /** 阶段总结列表。 */
    public List<PaperStageSummary> listStageSummaries(int limit) {
        int n = clamp(limit, 50, 200);
        return stageSummaryMapper.selectList(new LambdaQueryWrapper<PaperStageSummary>()
                .orderByDesc(PaperStageSummary::getCreatedAt)
                .last("LIMIT " + n));
    }

    /** 阶段总结详情。 */
    public PaperStageSummary getStageSummary(String id) {
        PaperStageSummary row = stageSummaryMapper.selectById(id);
        if (row == null) {
            throw new IllegalArgumentException("阶段总结不存在");
        }
        return row;
    }

    /** 手工新建阶段总结。 */
    @Transactional
    public PaperStageSummary createStageSummary(PaperStageSummaryRequest req) {
        if (req == null || !StringUtils.hasText(req.getSummaryText())) {
            throw new IllegalArgumentException("summaryText 不能为空");
        }
        PaperStageSummary row = new PaperStageSummary();
        row.setId(UUID.randomUUID().toString().replace("-", ""));
        applyStage(row, req, true);
        if (row.getStageNo() == null) {
            row.setStageNo(nextStageNo());
        }
        if (row.getEndedAt() == null) {
            row.setEndedAt(LocalDateTime.now());
        }
        if (row.getStartCash() == null) {
            row.setStartCash(BigDecimal.ZERO);
        }
        if (row.getEndEquity() == null) {
            row.setEndEquity(BigDecimal.ZERO);
        }
        if (row.getTradeCount() == null) {
            row.setTradeCount(0);
        }
        if (row.getTotalFee() == null) {
            row.setTotalFee(BigDecimal.ZERO);
        }
        row.setCreatedAt(LocalDateTime.now());
        stageSummaryMapper.insert(row);
        return row;
    }

    /** 更新阶段总结。 */
    @Transactional
    public PaperStageSummary updateStageSummary(String id, PaperStageSummaryRequest req) {
        PaperStageSummary row = getStageSummary(id);
        applyStage(row, req, false);
        stageSummaryMapper.updateById(row);
        return row;
    }

    /** 删除阶段总结。 */
    @Transactional
    public void deleteStageSummary(String id) {
        if (stageSummaryMapper.deleteById(id) == 0) {
            throw new IllegalArgumentException("阶段总结不存在");
        }
    }

    // —— AI 决策 ——

    /** 决策列表。 */
    public List<PaperAiDecisionVO> listDecisions(int limit) {
        int n = clamp(limit, 50, 200);
        List<PaperAiDecision> rows = decisionMapper.selectList(new LambdaQueryWrapper<PaperAiDecision>()
                .orderByDesc(PaperAiDecision::getCreatedAt)
                .last("LIMIT " + n));
        List<PaperAiDecisionVO> out = new ArrayList<>();
        for (PaperAiDecision row : rows) {
            out.add(toDecisionVo(row));
        }
        return out;
    }

    /** 决策详情。 */
    public PaperAiDecisionVO getDecision(String id) {
        return toDecisionVo(requireDecision(id));
    }

    /** 编辑决策字段。 */
    @Transactional
    public PaperAiDecisionVO updateDecision(String id, PaperAiDecisionUpdateRequest req) {
        PaperAiDecision d = requireDecision(id);
        if (req == null) {
            return toDecisionVo(d);
        }
        if (StringUtils.hasText(req.getAction())) {
            d.setAction(req.getAction().trim());
        }
        if (req.getSymbol() != null) {
            d.setSymbol(blankToNull(req.getSymbol()));
        }
        if (req.getName() != null) {
            d.setName(blankToNull(req.getName()));
        }
        if (req.getQty() != null) {
            d.setQty(req.getQty());
        }
        if (req.getRefPrice() != null) {
            d.setRefPrice(req.getRefPrice());
        }
        if (req.getConfidence() != null) {
            d.setConfidence(req.getConfidence());
        }
        if (req.getRationale() != null) {
            d.setRationale(req.getRationale());
        }
        if (StringUtils.hasText(req.getExecStatus())) {
            d.setExecStatus(req.getExecStatus().trim());
        }
        if (req.getSkipReason() != null) {
            d.setSkipReason(req.getSkipReason());
        }
        if (req.getNeedsHumanReview() != null) {
            d.setNeedsHumanReview(req.getNeedsHumanReview());
        }
        decisionMapper.updateById(d);
        return toDecisionVo(d);
    }

    /** 删除决策。 */
    @Transactional
    public void deleteDecision(String id) {
        if (decisionMapper.deleteById(id) == 0) {
            throw new IllegalArgumentException("决策不存在");
        }
    }

    // —— 搜集轮次 ——

    /** 搜集轮次列表。 */
    public List<PaperResearchRun> listResearchRuns(int limit) {
        int n = clamp(limit, 50, 200);
        return researchRunMapper.selectList(new LambdaQueryWrapper<PaperResearchRun>()
                .orderByDesc(PaperResearchRun::getCreatedAt)
                .last("LIMIT " + n));
    }

    /** 搜集轮次详情。 */
    public PaperResearchRun getResearchRun(String id) {
        PaperResearchRun run = researchRunMapper.selectById(id);
        if (run == null) {
            throw new IllegalArgumentException("搜集轮次不存在");
        }
        return run;
    }

    /** 删除搜集轮次。 */
    @Transactional
    public void deleteResearchRun(String id) {
        if (researchRunMapper.deleteById(id) == 0) {
            throw new IllegalArgumentException("搜集轮次不存在");
        }
    }

    // —— 研究卡片 ——

    /** 研究卡片列表（含未留下）。 */
    public List<PaperResearchNoteAdminVO> listResearchNotes(int limit) {
        int n = clamp(limit, 50, 200);
        List<PaperResearchNote> rows = researchNoteMapper.selectList(new LambdaQueryWrapper<PaperResearchNote>()
                .orderByDesc(PaperResearchNote::getUpdatedAt)
                .last("LIMIT " + n));
        List<PaperResearchNoteAdminVO> out = new ArrayList<>();
        for (PaperResearchNote row : rows) {
            out.add(toNoteAdminVo(row));
        }
        return out;
    }

    /** 研究卡片详情。 */
    public PaperResearchNoteAdminVO getResearchNote(String symbol) {
        return toNoteAdminVo(requireNote(symbol));
    }

    /** 新建研究卡片。 */
    @Transactional
    public PaperResearchNoteAdminVO createResearchNote(PaperResearchNoteRequest req) {
        if (req == null || !StringUtils.hasText(req.getSymbol())) {
            throw new IllegalArgumentException("symbol 不能为空");
        }
        String symbol = req.getSymbol().trim().toUpperCase();
        if (researchNoteMapper.selectById(symbol) != null) {
            throw new IllegalArgumentException("该代码卡片已存在");
        }
        PaperResearchNote row = new PaperResearchNote();
        row.setSymbol(symbol);
        applyNote(row, req, true);
        if (row.getKept() == null) {
            row.setKept(false);
        }
        LocalDateTime now = LocalDateTime.now();
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        researchNoteMapper.insert(row);
        return toNoteAdminVo(row);
    }

    /** 更新研究卡片。 */
    @Transactional
    public PaperResearchNoteAdminVO updateResearchNote(String symbol, PaperResearchNoteRequest req) {
        PaperResearchNote row = requireNote(symbol);
        applyNote(row, req, false);
        row.setUpdatedAt(LocalDateTime.now());
        researchNoteMapper.updateById(row);
        return toNoteAdminVo(row);
    }

    /** 删除研究卡片。 */
    @Transactional
    public void deleteResearchNote(String symbol) {
        if (researchNoteMapper.deleteById(symbol) == 0) {
            throw new IllegalArgumentException("研究卡片不存在");
        }
    }

    private void applyStage(PaperStageSummary row, PaperStageSummaryRequest req, boolean creating) {
        if (req.getStageNo() != null) {
            row.setStageNo(req.getStageNo());
        }
        if (req.getStartedAt() != null || creating) {
            row.setStartedAt(req.getStartedAt());
        }
        if (req.getEndedAt() != null) {
            row.setEndedAt(req.getEndedAt());
        }
        if (req.getStartCash() != null) {
            row.setStartCash(req.getStartCash());
        }
        if (req.getEndEquity() != null) {
            row.setEndEquity(req.getEndEquity());
        }
        if (req.getTradeCount() != null) {
            row.setTradeCount(req.getTradeCount());
        }
        if (req.getWinRate() != null) {
            row.setWinRate(req.getWinRate());
        }
        if (req.getPayoffRatio() != null) {
            row.setPayoffRatio(req.getPayoffRatio());
        }
        if (req.getMaxDrawdown() != null) {
            row.setMaxDrawdown(req.getMaxDrawdown());
        }
        if (req.getAvgHoldDays() != null) {
            row.setAvgHoldDays(req.getAvgHoldDays());
        }
        if (req.getTotalFee() != null) {
            row.setTotalFee(req.getTotalFee());
        }
        if (req.getLossSources() != null) {
            row.setLossSources(req.getLossSources());
        }
        if (StringUtils.hasText(req.getSummaryText())) {
            row.setSummaryText(req.getSummaryText().trim());
        } else if (creating) {
            throw new IllegalArgumentException("summaryText 不能为空");
        }
    }

    private void applyNote(PaperResearchNote row, PaperResearchNoteRequest req, boolean creating) {
        if (req == null) {
            if (creating) {
                throw new IllegalArgumentException("请求体不能为空");
            }
            return;
        }
        if (req.getName() != null) {
            row.setName(blankToNull(req.getName()));
        }
        if (req.getStory() != null) {
            row.setStory(req.getStory());
        }
        if (req.getRisks() != null) {
            row.setRisks(req.getRisks());
        }
        if (req.getKept() != null) {
            row.setKept(req.getKept());
        }
        if (req.getRejectReason() != null) {
            row.setRejectReason(req.getRejectReason());
        }
        if (req.getSourcesJson() != null) {
            row.setSourcesJson(req.getSourcesJson());
        }
        if (req.getQuoteSummary() != null) {
            row.setQuoteSummary(req.getQuoteSummary());
        }
    }

    private int nextStageNo() {
        PaperStageSummary latest = stageSummaryMapper.selectOne(new LambdaQueryWrapper<PaperStageSummary>()
                .orderByDesc(PaperStageSummary::getStageNo)
                .last("LIMIT 1"));
        return latest == null || latest.getStageNo() == null ? 1 : latest.getStageNo() + 1;
    }

    private PaperAiDecision requireDecision(String id) {
        PaperAiDecision d = decisionMapper.selectById(id);
        if (d == null) {
            throw new IllegalArgumentException("决策不存在");
        }
        return d;
    }

    private PaperResearchNote requireNote(String symbol) {
        if (!StringUtils.hasText(symbol)) {
            throw new IllegalArgumentException("symbol 不能为空");
        }
        PaperResearchNote row = researchNoteMapper.selectById(symbol.trim());
        if (row == null) {
            throw new IllegalArgumentException("研究卡片不存在");
        }
        return row;
    }

    private PaperAiDecisionVO toDecisionVo(PaperAiDecision d) {
        PaperAiDecisionVO vo = new PaperAiDecisionVO();
        vo.setId(d.getId());
        vo.setRunId(d.getRunId());
        vo.setAction(d.getAction());
        vo.setSymbol(d.getSymbol());
        vo.setName(d.getName());
        vo.setQty(d.getQty());
        vo.setRefPrice(d.getRefPrice());
        vo.setTakeProfitPct(d.getTakeProfitPct());
        vo.setStopLossPct(d.getStopLossPct());
        vo.setMaxHoldDays(d.getMaxHoldDays());
        vo.setCooldownDays(d.getCooldownDays());
        vo.setConfidence(d.getConfidence());
        vo.setRationale(d.getRationale());
        vo.setNeedsHumanReview(d.getNeedsHumanReview());
        vo.setExecStatus(d.getExecStatus());
        vo.setSkipReason(d.getSkipReason());
        vo.setOrderId(d.getOrderId());
        if (d.getCreatedAt() != null) {
            vo.setCreatedAt(d.getCreatedAt().format(TS));
        }
        if (StringUtils.hasText(d.getRisksJson())) {
            try {
                vo.setRisks(objectMapper.readValue(d.getRisksJson(), STRING_LIST));
            } catch (Exception e) {
                vo.setRisks(List.of());
            }
        }
        return vo;
    }

    private PaperResearchNoteAdminVO toNoteAdminVo(PaperResearchNote row) {
        PaperResearchNoteAdminVO vo = new PaperResearchNoteAdminVO();
        vo.setSymbol(row.getSymbol());
        vo.setName(row.getName());
        vo.setQuoteSummary(row.getQuoteSummary());
        vo.setStory(row.getStory());
        vo.setRisks(row.getRisks());
        vo.setKept(row.getKept());
        vo.setRejectReason(row.getRejectReason());
        vo.setSourcesJson(row.getSourcesJson());
        vo.setRunId(row.getRunId());
        if (row.getCreatedAt() != null) {
            vo.setCreatedAt(row.getCreatedAt().format(TS));
        }
        if (row.getUpdatedAt() != null) {
            vo.setUpdatedAt(row.getUpdatedAt().format(TS));
        }
        return vo;
    }

    private static int clamp(int limit, int def, int max) {
        if (limit <= 0) {
            return def;
        }
        return Math.min(limit, max);
    }

    private static String blankToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
