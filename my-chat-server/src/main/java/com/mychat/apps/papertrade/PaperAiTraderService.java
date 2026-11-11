package com.mychat.apps.papertrade;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mychat.apps.market.EtfPremiumClient;
import com.mychat.apps.papertrade.entity.dto.PaperOrderRequest;
import com.mychat.apps.papertrade.entity.po.PaperAiDecision;
import com.mychat.apps.papertrade.entity.po.PaperAiRun;
import com.mychat.apps.papertrade.entity.po.PaperOrder;
import com.mychat.apps.papertrade.entity.po.PaperPosition;
import com.mychat.apps.papertrade.entity.po.PaperStrategy;
import com.mychat.apps.papertrade.entity.vo.PaperAiDecisionVO;
import com.mychat.apps.papertrade.entity.vo.PaperAiRunVO;
import com.mychat.apps.papertrade.entity.vo.PaperAiSubmitVO;
import com.mychat.apps.papertrade.entity.vo.PaperOrderVO;
import com.mychat.apps.papertrade.mapper.PaperAiDecisionMapper;
import com.mychat.apps.papertrade.mapper.PaperAiRunMapper;
import com.mychat.job.AsyncJobService;
import com.mychat.vo.AsyncJobVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * AI 纸面交易：提交异步轮次、解析 JSON、风控闸门、自动/挂起执行。
 */
@Slf4j
@Service
public class PaperAiTraderService {

    public static final String JOB_TYPE = "paper_trade_ai_round";
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final PaperAiRunMapper runMapper;
    private final PaperAiDecisionMapper decisionMapper;
    private final PaperStrategyService strategyService;
    private final PaperAiContextBuilder contextBuilder;
    private final PaperRiskGuard riskGuard;
    private final PaperTradeService tradeService;
    private final PaperTradeProperties properties;
    private final PaperPositionExitService exitService;
    private final AsyncJobService asyncJobService;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PaperAiTraderService(PaperAiRunMapper runMapper,
                                PaperAiDecisionMapper decisionMapper,
                                PaperStrategyService strategyService,
                                PaperAiContextBuilder contextBuilder,
                                PaperRiskGuard riskGuard,
                                PaperTradeService tradeService,
                                PaperTradeProperties properties,
                                PaperPositionExitService exitService,
                                AsyncJobService asyncJobService,
                                @Qualifier("agentWorkflowChatClient") ChatClient chatClient) {
        this.runMapper = runMapper;
        this.decisionMapper = decisionMapper;
        this.strategyService = strategyService;
        this.contextBuilder = contextBuilder;
        this.riskGuard = riskGuard;
        this.tradeService = tradeService;
        this.properties = properties;
        this.exitService = exitService;
        this.asyncJobService = asyncJobService;
        this.chatClient = chatClient;
    }

    /** 提交一轮：立刻返回 runId + jobId。 */
    @Transactional
    public PaperAiSubmitVO submitRun() {
        PaperAiRun run = new PaperAiRun();
        run.setId(UUID.randomUUID().toString().replace("-", ""));
        run.setStatus(PaperAiRun.STATUS_PENDING);
        runMapper.insert(run);

        AsyncJobVO job = asyncJobService.submit(JOB_TYPE, "模拟炒股 AI 一轮", run.getId(), null, true);
        run.setJobId(job.getId());
        runMapper.updateById(run);

        PaperAiSubmitVO vo = new PaperAiSubmitVO();
        vo.setRunId(run.getId());
        vo.setJobId(job.getId());
        vo.setStatus(run.getStatus());
        return vo;
    }

    /** JobHandler 入口。 */
    public void runRound(String runId) throws Exception {
        if (!StringUtils.hasText(runId)) {
            throw new IllegalArgumentException("paper_trade_ai_round 缺少 refId");
        }
        PaperAiRun run = runMapper.selectById(runId.trim());
        if (run == null) {
            throw new IllegalArgumentException("AI 轮次不存在");
        }
        run.setStatus(PaperAiRun.STATUS_RUNNING);
        runMapper.updateById(run);
        try {
            // 1) 持仓止盈/止损/最长持有先于模型，并立即走执行闸门
            exitService.scanAndEnqueue(run.getId());
            flushPendingExits(run.getId());

            // 2) 组装上下文并调模型
            PaperAiContextBuilder.BuiltContext ctx = contextBuilder.build();
            run.setContextJson(ctx.contextJson());
            runMapper.updateById(run);

            PaperStrategy strategy = strategyService.requireDefault();
            String raw = callAgent(strategy.getContent(), ctx.contextJson());
            run.setRawResponse(raw);

            List<RawDecision> parsed = parseDecisions(raw);
            List<PaperPosition> positions = ctx.positions();
            Set<String> cds = ctx.cooldownSymbols();
            BigDecimal cash = ctx.cash();
            for (RawDecision rd : parsed) {
                PaperAiDecision d = toDecision(run.getId(), rd);
                decisionMapper.insert(d);
                processDecision(d, positions, cds, ctx.premiums(), cash, PaperOrder.SOURCE_AI);
                if (PaperAiDecision.EXEC_EXECUTED.equals(d.getExecStatus())) {
                    positions = tradeService.listPositionEntities();
                    cds = tradeService.activeCooldownSymbols();
                    cash = tradeService.requireAccount().getCash();
                }
            }

            run.setStatus(PaperAiRun.STATUS_SUCCEEDED);
            run.setFinishedAt(LocalDateTime.now());
            runMapper.updateById(run);
        } catch (Exception e) {
            run.setStatus(PaperAiRun.STATUS_FAILED);
            run.setErrorMessage(truncate(e.getMessage(), 1000));
            run.setFinishedAt(LocalDateTime.now());
            runMapper.updateById(run);
            throw e;
        }
    }

    /** 列表最近轮次。 */
    public List<PaperAiRunVO> listRuns(int limit) {
        int n = limit <= 0 ? 20 : Math.min(limit, 50);
        List<PaperAiRun> rows = runMapper.selectList(new LambdaQueryWrapper<PaperAiRun>()
                .orderByDesc(PaperAiRun::getCreatedAt)
                .last("LIMIT " + n));
        List<PaperAiRunVO> out = new ArrayList<>();
        for (PaperAiRun row : rows) {
            out.add(toRunVo(row, false));
        }
        return out;
    }

    /** 轮次详情含决策。 */
    public PaperAiRunVO getRun(String id) {
        PaperAiRun row = runMapper.selectById(id);
        if (row == null) {
            throw new IllegalArgumentException("AI 轮次不存在");
        }
        return toRunVo(row, true);
    }

    /** 人工确认待执行决策。 */
    @Transactional
    public PaperAiDecisionVO confirm(String decisionId) {
        PaperAiDecision d = requirePending(decisionId);
        List<PaperPosition> positions = tradeService.listPositionEntities();
        Set<String> cds = tradeService.activeCooldownSymbols();
        BigDecimal cash = tradeService.requireAccount().getCash();
        // 确认时强制执行，忽略复核标记
        d.setNeedsHumanReview(false);
        forceExecute(d, positions, cds, Map.of(), cash, PaperOrder.SOURCE_AI);
        return toDecisionVo(decisionMapper.selectById(d.getId()));
    }

    /** 忽略待确认决策。 */
    @Transactional
    public PaperAiDecisionVO dismiss(String decisionId) {
        PaperAiDecision d = requirePending(decisionId);
        d.setExecStatus(PaperAiDecision.EXEC_DISMISSED);
        d.setSkipReason("USER_DISMISSED");
        decisionMapper.updateById(d);
        return toDecisionVo(d);
    }

    /** 最近决策（跨轮）。 */
    public List<PaperAiDecisionVO> listRecentDecisions(int limit) {
        int n = limit <= 0 ? 30 : Math.min(limit, 100);
        List<PaperAiDecision> rows = decisionMapper.selectList(new LambdaQueryWrapper<PaperAiDecision>()
                .orderByDesc(PaperAiDecision::getCreatedAt)
                .last("LIMIT " + n));
        List<PaperAiDecisionVO> out = new ArrayList<>();
        for (PaperAiDecision row : rows) {
            out.add(toDecisionVo(row));
        }
        return out;
    }

    /** 处理本轮出场扫描写入的 PENDING SELL。 */
    private void flushPendingExits(String runId) {
        List<PaperAiDecision> pending = decisionMapper.selectList(new LambdaQueryWrapper<PaperAiDecision>()
                .eq(PaperAiDecision::getRunId, runId)
                .eq(PaperAiDecision::getExecStatus, PaperAiDecision.EXEC_PENDING));
        List<PaperPosition> positions = tradeService.listPositionEntities();
        Set<String> cds = tradeService.activeCooldownSymbols();
        BigDecimal cash = tradeService.requireAccount().getCash();
        for (PaperAiDecision d : pending) {
            processDecision(d, positions, cds, Map.of(), cash, PaperOrder.SOURCE_EXIT);
            if (PaperAiDecision.EXEC_EXECUTED.equals(d.getExecStatus())) {
                positions = tradeService.listPositionEntities();
                cds = tradeService.activeCooldownSymbols();
                cash = tradeService.requireAccount().getCash();
            }
        }
    }

    private PaperAiDecision requirePending(String decisionId) {
        if (!StringUtils.hasText(decisionId)) {
            throw new IllegalArgumentException("decisionId 不能为空");
        }
        PaperAiDecision d = decisionMapper.selectById(decisionId.trim());
        if (d == null) {
            throw new IllegalArgumentException("决策不存在");
        }
        if (!PaperAiDecision.EXEC_PENDING.equals(d.getExecStatus())) {
            throw new IllegalArgumentException("决策不是待确认状态");
        }
        return d;
    }

    private String callAgent(String strategy, String contextJson) {
        String system = strategy + """


                【平台硬约束】
                1. 只输出 JSON 数组，不要 Markdown。
                2. 禁止编造未在上下文给出的行情数字；可引用 candidates/positions 中的字段。
                3. HOLD/FLAT 不下单；BUY/SELL 才进入执行闸门。
                4. 数量优先 qty（A 股 100 股整数倍）；缺省可用 allocationPct * cash / refPrice 向下取整到 100。
                5. ETF/QDII 溢价拿不到或过高时不要建议买入。
                """;
        String user = """
                当前账户与行情上下文（JSON）：
                %s

                请给出本轮决策数组，每项字段：
                action,symbol,name,executeAt,qty,allocationPct,refPrice,takeProfitPct,stopLossPct,
                maxHoldDays,cooldownDays,confidence,rationale,risks,needsHumanReview
                """.formatted(contextJson);
        return chatClient.prompt()
                .system(system)
                .user(user)
                .call()
                .content();
    }

    List<RawDecision> parseDecisions(String text) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        String json = text.trim();
        int arrStart = json.indexOf('[');
        int arrEnd = json.lastIndexOf(']');
        int objStart = json.indexOf('{');
        int objEnd = json.lastIndexOf('}');
        try {
            if (arrStart >= 0 && arrEnd > arrStart) {
                return objectMapper.readValue(json.substring(arrStart, arrEnd + 1),
                        new TypeReference<List<RawDecision>>() {
                        });
            }
            if (objStart >= 0 && objEnd > objStart) {
                RawDecision one = objectMapper.readValue(json.substring(objStart, objEnd + 1), RawDecision.class);
                return List.of(one);
            }
        } catch (Exception e) {
            throw new IllegalStateException("无法解析 AI 决策 JSON: " + e.getMessage());
        }
        return List.of();
    }

    private PaperAiDecision toDecision(String runId, RawDecision rd) {
        PaperAiDecision d = new PaperAiDecision();
        d.setId(UUID.randomUUID().toString().replace("-", ""));
        d.setRunId(runId);
        String action = rd.action == null ? "HOLD" : rd.action.trim().toUpperCase(Locale.ROOT);
        d.setAction(action);
        d.setSymbol(rd.symbol == null ? null : rd.symbol.trim().toUpperCase(Locale.ROOT));
        d.setName(rd.name);
        d.setQty(resolveQty(rd));
        d.setRefPrice(rd.refPrice == null ? null : BigDecimal.valueOf(rd.refPrice).setScale(4, RoundingMode.HALF_UP));
        d.setTakeProfitPct(toBd(rd.takeProfitPct));
        d.setStopLossPct(toBd(rd.stopLossPct));
        d.setMaxHoldDays(rd.maxHoldDays);
        d.setCooldownDays(rd.cooldownDays);
        d.setConfidence(toBd(rd.confidence));
        d.setRationale(rd.rationale);
        try {
            d.setRisksJson(rd.risks == null ? "[]" : objectMapper.writeValueAsString(rd.risks));
        } catch (Exception e) {
            d.setRisksJson("[]");
        }
        d.setNeedsHumanReview(Boolean.TRUE.equals(rd.needsHumanReview));
        d.setExecStatus(PaperAiDecision.EXEC_PENDING);
        return d;
    }

    private Integer resolveQty(RawDecision rd) {
        if (rd.qty != null && rd.qty > 0) {
            return floorLot(rd.qty);
        }
        if (rd.allocationPct != null && rd.refPrice != null && rd.refPrice > 0) {
            BigDecimal cash = tradeService.requireAccount().getCash();
            if (cash == null || cash.compareTo(BigDecimal.ZERO) <= 0) {
                return null;
            }
            double spend = cash.doubleValue() * rd.allocationPct;
            int raw = (int) Math.floor(spend / rd.refPrice);
            return floorLot(raw);
        }
        return null;
    }

    private static int floorLot(int qty) {
        if (qty < 100) {
            return qty; // 非 A 股可能 <100；闸门/撮合再验
        }
        return qty - (qty % 100);
    }

    private void processDecision(PaperAiDecision d,
                                 List<PaperPosition> positions,
                                 Set<String> cooldownSymbols,
                                 Map<String, EtfPremiumClient.PremiumQuote> premiums,
                                 BigDecimal cash,
                                 String source) {
        if (!PaperAiDecision.EXEC_PENDING.equals(d.getExecStatus())) {
            return;
        }
        if (PaperAiDecision.ACTION_HOLD.equals(d.getAction())
                || PaperAiDecision.ACTION_FLAT.equals(d.getAction())) {
            d.setExecStatus(PaperAiDecision.EXEC_SKIPPED);
            d.setSkipReason("NO_ORDER");
            decisionMapper.updateById(d);
            return;
        }

        boolean needReview = Boolean.TRUE.equals(d.getNeedsHumanReview())
                || confidenceLow(d.getConfidence());
        if (properties.isAutoExecute() && !needReview) {
            forceExecute(d, positions, cooldownSymbols, premiums, cash, source);
        } else if (!properties.isAutoExecute() || needReview) {
            // 保持 PENDING 待确认；先过一遍硬风控，违规则直接 SKIPPED/REJECTED
            String reject = precheck(d, positions, cooldownSymbols, premiums, cash);
            if (reject != null) {
                d.setExecStatus(PaperAiDecision.EXEC_SKIPPED);
                d.setSkipReason(reject);
                decisionMapper.updateById(d);
            } else {
                decisionMapper.updateById(d);
            }
        }
    }

    private void forceExecute(PaperAiDecision d,
                              List<PaperPosition> positions,
                              Set<String> cooldownSymbols,
                              Map<String, EtfPremiumClient.PremiumQuote> premiums,
                              BigDecimal cash,
                              String source) {
        String reject = precheck(d, positions, cooldownSymbols, premiums, cash);
        if (reject != null) {
            d.setExecStatus(PaperAiDecision.EXEC_SKIPPED);
            d.setSkipReason(reject);
            decisionMapper.updateById(d);
            return;
        }
        PaperOrderRequest req = new PaperOrderRequest();
        req.setSymbol(d.getSymbol());
        req.setSide(d.getAction());
        req.setQty(d.getQty());
        req.setSource(source);
        req.setDecisionId(d.getId());
        req.setTakeProfitPct(d.getTakeProfitPct());
        req.setStopLossPct(d.getStopLossPct());
        req.setMaxHoldDays(d.getMaxHoldDays());
        req.setCooldownDays(d.getCooldownDays());
        PaperOrderVO order = tradeService.placeOrder(req);
        if (PaperOrder.STATUS_FILLED.equals(order.getStatus())) {
            d.setExecStatus(PaperAiDecision.EXEC_EXECUTED);
            d.setOrderId(order.getId());
            d.setSkipReason(null);
        } else {
            d.setExecStatus(PaperAiDecision.EXEC_REJECTED);
            d.setOrderId(order.getId());
            d.setSkipReason(order.getRejectReason());
        }
        decisionMapper.updateById(d);
    }

    private String precheck(PaperAiDecision d,
                            List<PaperPosition> positions,
                            Set<String> cooldownSymbols,
                            Map<String, EtfPremiumClient.PremiumQuote> premiums,
                            BigDecimal cash) {
        if (PaperAiDecision.ACTION_BUY.equals(d.getAction())) {
            EtfPremiumClient.PremiumQuote pq = premiums == null ? null : premiums.get(d.getSymbol());
            return riskGuard.rejectBuyReason(d, positions, cooldownSymbols, pq, cash);
        }
        if (PaperAiDecision.ACTION_SELL.equals(d.getAction())) {
            return riskGuard.rejectSellReason(d, positions);
        }
        return "UNSUPPORTED_ACTION";
    }

    private boolean confidenceLow(BigDecimal confidence) {
        if (confidence == null) {
            return true;
        }
        return confidence.doubleValue() < properties.getReviewConfidenceThreshold();
    }

    private PaperAiRunVO toRunVo(PaperAiRun row, boolean withDecisions) {
        PaperAiRunVO vo = new PaperAiRunVO();
        vo.setId(row.getId());
        vo.setJobId(row.getJobId());
        vo.setStatus(row.getStatus());
        vo.setContextJson(row.getContextJson());
        vo.setRawResponse(row.getRawResponse());
        vo.setErrorMessage(row.getErrorMessage());
        if (row.getCreatedAt() != null) {
            vo.setCreatedAt(row.getCreatedAt().format(TS));
        }
        if (row.getFinishedAt() != null) {
            vo.setFinishedAt(row.getFinishedAt().format(TS));
        }
        if (withDecisions) {
            List<PaperAiDecision> ds = decisionMapper.selectList(new LambdaQueryWrapper<PaperAiDecision>()
                    .eq(PaperAiDecision::getRunId, row.getId())
                    .orderByAsc(PaperAiDecision::getCreatedAt));
            List<PaperAiDecisionVO> list = new ArrayList<>();
            for (PaperAiDecision d : ds) {
                list.add(toDecisionVo(d));
            }
            vo.setDecisions(list);
        }
        return vo;
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

    private static BigDecimal toBd(Double v) {
        if (v == null || !Double.isFinite(v)) {
            return null;
        }
        return BigDecimal.valueOf(v).setScale(6, RoundingMode.HALF_UP);
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    /** 模型原始决策 DTO。 */
    public static class RawDecision {
        public String action;
        public String symbol;
        public String name;
        public String executeAt;
        public Integer qty;
        public Double allocationPct;
        public Double refPrice;
        public Double takeProfitPct;
        public Double stopLossPct;
        public Integer maxHoldDays;
        public Integer cooldownDays;
        public Double confidence;
        public String rationale;
        public List<String> risks;
        public Boolean needsHumanReview;
    }
}
