package com.mychat.apps.papertrade.trader;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mychat.apps.papertrade.PaperTradeProperties;
import com.mychat.apps.papertrade.agent.PaperFrameworkCall;
import com.mychat.apps.papertrade.ledger.PaperTradeService;
import com.mychat.apps.papertrade.ledger.entity.dto.PaperOrderRequest;
import com.mychat.apps.papertrade.research.PaperResearchService;
import com.mychat.apps.papertrade.risk.PaperGateContext;
import com.mychat.apps.papertrade.risk.PaperPositionExitService;
import com.mychat.apps.papertrade.risk.PaperRiskGuard;
import com.mychat.apps.papertrade.risk.PaperStageService;
import com.mychat.apps.papertrade.risk.PaperSymbolFacts;
import com.mychat.apps.papertrade.trader.entity.po.PaperAiDecision;
import com.mychat.apps.papertrade.trader.entity.po.PaperAiRun;
import com.mychat.apps.papertrade.ledger.entity.po.PaperOrder;
import com.mychat.apps.papertrade.ledger.entity.po.PaperPosition;
import com.mychat.apps.papertrade.trader.entity.po.PaperStrategy;
import com.mychat.apps.papertrade.trader.entity.vo.PaperAiDecisionVO;
import com.mychat.apps.papertrade.trader.entity.vo.PaperAiRunVO;
import com.mychat.apps.papertrade.trader.entity.vo.PaperAiSubmitVO;
import com.mychat.apps.papertrade.ledger.entity.vo.PaperOrderVO;
import com.mychat.apps.papertrade.trader.mapper.PaperAiDecisionMapper;
import com.mychat.apps.papertrade.trader.mapper.PaperAiRunMapper;
import com.mychat.job.AsyncJobService;
import com.mychat.vo.AsyncJobVO;
import lombok.extern.slf4j.Slf4j;
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
    private final PaperStageService stageService;
    private final AsyncJobService asyncJobService;
    private final PaperFrameworkCall frameworkCall;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public PaperAiTraderService(PaperAiRunMapper runMapper,
                                PaperAiDecisionMapper decisionMapper,
                                PaperStrategyService strategyService,
                                PaperAiContextBuilder contextBuilder,
                                PaperRiskGuard riskGuard,
                                PaperTradeService tradeService,
                                PaperTradeProperties properties,
                                PaperPositionExitService exitService,
                                PaperStageService stageService,
                                AsyncJobService asyncJobService,
                                PaperFrameworkCall frameworkCall) {
        this.runMapper = runMapper;
        this.decisionMapper = decisionMapper;
        this.strategyService = strategyService;
        this.contextBuilder = contextBuilder;
        this.riskGuard = riskGuard;
        this.tradeService = tradeService;
        this.properties = properties;
        this.exitService = exitService;
        this.stageService = stageService;
        this.asyncJobService = asyncJobService;
        this.frameworkCall = frameworkCall;
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
            // 1. 出场先于模型，跌停的持仓本轮不卖
            exitService.scanAndEnqueue(run.getId());
            flushPendingExits(run.getId(), baseGate());
            PaperTradeService.EquityMark mark = tradeService.markEquity();
            tradeService.refreshPeak(mark);

            // 2. 组上下文；现金买不起任何一手且已空仓时，先写总结再重置
            PaperAiContextBuilder.BuiltContext ctx = contextBuilder.build();
            if (shouldReset(ctx)) {
                stageService.summarizeAndReset(mark.equity());
                ctx = contextBuilder.build();
            }
            run.setContextJson(ctx.contextJson());
            runMapper.updateById(run);
            PaperGateContext gate = baseGate();
            gate.setPositions(ctx.positions());
            gate.setCooldownSymbols(ctx.cooldownSymbols());
            gate.setPremiums(ctx.premiums());
            gate.setFacts(ctx.facts());
            gate.setBestEtfSymbol(ctx.bestEtfSymbol());
            gate.setCash(ctx.cash());

            // 3. 调模型并逐条过闸门
            PaperStrategy strategy = strategyService.requireDefault();
            PaperFrameworkCall.Outcome outcome = callAgent(strategy.getContent(), ctx.contextJson());
            String raw = PaperResearchService.firstJson(outcome.answer(), outcome.observations());
            run.setRawResponse(raw);
            run.setFrameworkTrace(outcome.traceJson());

            List<RawDecision> parsed = parseDecisions(raw);
            for (RawDecision rd : parsed) {
                PaperAiDecision d = toDecision(run.getId(), rd);
                decisionMapper.insert(d);
                if (outsidePool(d, ctx.candidateSymbols())) {
                    d.setExecStatus(PaperAiDecision.EXEC_SKIPPED);
                    d.setSkipReason("NOT_IN_POOL");
                    decisionMapper.updateById(d);
                    continue;
                }
                processDecision(d, gate, PaperOrder.SOURCE_AI);
                if (PaperAiDecision.EXEC_EXECUTED.equals(d.getExecStatus())) {
                    refreshGateBook(gate);
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
        PaperAiContextBuilder.BuiltContext ctx = contextBuilder.build();
        PaperGateContext gate = baseGate();
        gate.setPositions(ctx.positions());
        gate.setCooldownSymbols(ctx.cooldownSymbols());
        gate.setPremiums(ctx.premiums());
        gate.setFacts(ctx.facts());
        gate.setBestEtfSymbol(ctx.bestEtfSymbol());
        gate.setCash(ctx.cash());
        d.setNeedsHumanReview(false);
        String reject = applyGate(d, gate);
        if (reject != null) {
            d.setExecStatus(PaperAiDecision.EXEC_SKIPPED);
            d.setSkipReason(reject);
            decisionMapper.updateById(d);
            return toDecisionVo(d);
        }
        place(d, PaperOrder.SOURCE_AI);
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
    private void flushPendingExits(String runId, PaperGateContext gate) {
        List<PaperAiDecision> pending = decisionMapper.selectList(new LambdaQueryWrapper<PaperAiDecision>()
                .eq(PaperAiDecision::getRunId, runId)
                .eq(PaperAiDecision::getExecStatus, PaperAiDecision.EXEC_PENDING));
        for (PaperAiDecision d : pending) {
            processDecision(d, gate, PaperOrder.SOURCE_EXIT);
            if (PaperAiDecision.EXEC_EXECUTED.equals(d.getExecStatus())) {
                refreshGateBook(gate);
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

    private PaperFrameworkCall.Outcome callAgent(String strategy, String contextJson) {
        String system = strategy + """


                【平台硬约束】
                1. 只输出 JSON 数组，不要 Markdown。
                2. 禁止编造未在上下文给出的行情数字；可引用 candidates/positions 中的字段。
                3. hold/flat 不下单；buy/sell 才进入执行闸门。动作请用小写或大写均可。
                4. 数量用 quantity（A 股 100 股整数倍）。不足 1 手会被拒绝，不要向上凑整。
                5. 买入必须给出 stop_loss 和 take_profit（小数，如 0.03）。ETF 溢价拿不到或过高时不要建议买入。
                6. 已持有的标的不要再买。连续止损暂停、回撤收紧时遵守上下文里的限制。
                7. 只能买卖 candidates 与 positions 里出现的代码。不要搜索新标的，不要使用知识库。
                """;
        String user = """
                当前账户与行情上下文（JSON）：
                %s

                请给出本轮决策数组，每项字段：
                action,symbol,name,asset_type,planned_time,quantity,capital_ratio,reference_price,
                take_profit,stop_loss,max_hold_days,cooldown_until,confidence,reasoning,risk_flags,manual_review
                """.formatted(contextJson);
        PaperFrameworkCall.Outcome outcome = frameworkCall.call(system + "\n" + user, 3);
        return outcome;
    }

    /** 买入或卖出的代码必须来自本轮候选（持仓和已留卡片）。 */
    private static boolean outsidePool(PaperAiDecision d, java.util.Set<String> symbols) {
        if (!"BUY".equals(d.getAction()) && !"SELL".equals(d.getAction())) {
            return false;
        }
        String symbol = d.getSymbol() == null ? "" : d.getSymbol().trim().toUpperCase(Locale.ROOT);
        return !StringUtils.hasText(symbol) || symbols == null || !symbols.contains(symbol);
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
                try {
                    return objectMapper.readValue(json.substring(arrStart, arrEnd + 1),
                            new TypeReference<List<RawDecision>>() {
                            });
                } catch (Exception arrayFail) {
                    // 单个对象里的 risks 数组不能当成决策列表
                    if (objStart >= 0 && objEnd > objStart) {
                        RawDecision one = objectMapper.readValue(json.substring(objStart, objEnd + 1), RawDecision.class);
                        return List.of(one);
                    }
                    throw arrayFail;
                }
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
        d.setRefPrice(rd.referencePrice == null ? null : BigDecimal.valueOf(rd.referencePrice).setScale(4, RoundingMode.HALF_UP));
        d.setTakeProfitPct(toBd(rd.takeProfit));
        d.setStopLossPct(toBd(rd.stopLoss));
        d.setMaxHoldDays(rd.maxHoldDays);
        d.setCooldownDays(resolveCooldown(rd));
        d.setConfidence(toBd(rd.confidence));
        d.setRationale(rd.reasoning);
        try {
            d.setRisksJson(rd.riskFlags == null ? "[]" : objectMapper.writeValueAsString(rd.riskFlags));
        } catch (Exception e) {
            d.setRisksJson("[]");
        }
        d.setNeedsHumanReview(Boolean.TRUE.equals(rd.manualReview));
        d.setExecStatus(PaperAiDecision.EXEC_PENDING);
        return d;
    }

    private Integer resolveQty(RawDecision rd) {
        if (rd.quantity != null && rd.quantity > 0) {
            return floorLot(rd.quantity);
        }
        if (rd.capitalRatio != null && rd.referencePrice != null && rd.referencePrice > 0) {
            BigDecimal cash = tradeService.requireAccount().getCash();
            if (cash == null || cash.compareTo(BigDecimal.ZERO) <= 0) {
                return null;
            }
            double spend = cash.doubleValue() * rd.capitalRatio;
            int raw = (int) Math.floor(spend / rd.referencePrice);
            return floorLot(raw);
        }
        return null;
    }

    /** cooldown_until 为整数时当作冷却天数；日期字符串则交给撮合默认。 */
    private static Integer resolveCooldown(RawDecision rd) {
        if (rd.cooldownDays != null && rd.cooldownDays > 0) {
            return rd.cooldownDays;
        }
        if (rd.cooldownUntil == null || rd.cooldownUntil.isNull()) {
            return null;
        }
        if (rd.cooldownUntil.isNumber()) {
            return rd.cooldownUntil.intValue();
        }
        String text = rd.cooldownUntil.asText();
        if (text != null && text.matches("\\d+")) {
            return Integer.parseInt(text);
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
                                 PaperGateContext gate,
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

        String reject = applyGate(d, gate);
        if (reject != null) {
            d.setExecStatus(PaperAiDecision.EXEC_SKIPPED);
            d.setSkipReason(reject);
            decisionMapper.updateById(d);
            return;
        }
        boolean needReview = Boolean.TRUE.equals(d.getNeedsHumanReview())
                || confidenceLow(d.getConfidence());
        if (properties.isAutoExecute() && !needReview) {
            place(d, source);
        } else {
            decisionMapper.updateById(d);
        }
    }

    private String applyGate(PaperAiDecision d, PaperGateContext gate) {
        if (PaperAiDecision.ACTION_BUY.equals(d.getAction())) {
            return riskGuard.assessBuy(d, gate);
        }
        if (PaperAiDecision.ACTION_SELL.equals(d.getAction())) {
            PaperSymbolFacts facts = gate == null || gate.getFacts() == null
                    ? null : gate.getFacts().get(d.getSymbol());
            List<PaperPosition> positions = gate == null ? List.of() : gate.getPositions();
            return riskGuard.rejectSellReason(d, positions, facts);
        }
        return "UNSUPPORTED_ACTION";
    }

    private void place(PaperAiDecision d, String source) {
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
        if (PaperAiDecision.ACTION_SELL.equals(d.getAction())) {
            req.setExitReason(exitReasonOf(d, source));
        }
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

    private PaperGateContext baseGate() {
        PaperTradeService.EquityMark mark = tradeService.markEquity();
        tradeService.refreshPeak(mark);
        PaperGateContext gate = new PaperGateContext();
        gate.setPositions(tradeService.listPositionEntities());
        gate.setCooldownSymbols(tradeService.activeCooldownSymbols());
        gate.setCash(tradeService.requireAccount().getCash());
        gate.setEquity(mark.equity());
        gate.setBuyPaused(tradeService.isBuyPaused());
        gate.setDrawdownRestricted(mark.complete() && tradeService.isDrawdownRestricted(mark.equity()));
        double budget = gate.isDrawdownRestricted()
                ? properties.getRiskBudgetTightPct()
                : properties.getRiskBudgetPct();
        gate.setRiskBudgetPct(budget <= 0 ? 0.02 : budget);
        gate.setPremiums(Map.of());
        gate.setFacts(Map.of());
        return gate;
    }

    private void refreshGateBook(PaperGateContext gate) {
        gate.setPositions(tradeService.listPositionEntities());
        gate.setCooldownSymbols(tradeService.activeCooldownSymbols());
        gate.setCash(tradeService.requireAccount().getCash());
        gate.setEquity(tradeService.markEquity().equity());
        gate.setBuyPaused(tradeService.isBuyPaused());
    }

    private static boolean shouldReset(PaperAiContextBuilder.BuiltContext ctx) {
        if (ctx == null || !ctx.quoteDataAvailable() || ctx.cheapestLotCost() == null) {
            return false;
        }
        if (ctx.positions() != null && !ctx.positions().isEmpty()) {
            return false;
        }
        BigDecimal cash = ctx.cash() == null ? BigDecimal.ZERO : ctx.cash();
        return cash.compareTo(ctx.cheapestLotCost()) < 0;
    }

    private static String exitReasonOf(PaperAiDecision d, String source) {
        if (PaperOrder.SOURCE_EXIT.equals(source) && d.getRationale() != null) {
            String rationale = d.getRationale();
            if (rationale.contains(PaperOrder.EXIT_STOP_LOSS)) {
                return PaperOrder.EXIT_STOP_LOSS;
            }
            if (rationale.contains(PaperOrder.EXIT_TAKE_PROFIT)) {
                return PaperOrder.EXIT_TAKE_PROFIT;
            }
            if (rationale.contains(PaperOrder.EXIT_MAX_HOLD)) {
                return PaperOrder.EXIT_MAX_HOLD;
            }
        }
        if (PaperOrder.SOURCE_AI.equals(source)) {
            return PaperOrder.EXIT_AI;
        }
        return PaperOrder.EXIT_MANUAL;
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
        vo.setFrameworkTrace(row.getFrameworkTrace());
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

    /** 模型原始决策。新旧字段名都能读。 */
    public static class RawDecision {
        public String action;
        public String symbol;
        public String name;
        @JsonAlias({"executeAt", "planned_time"})
        public String plannedTime;
        @JsonAlias("qty")
        public Integer quantity;
        @JsonAlias({"allocationPct", "capital_ratio"})
        public Double capitalRatio;
        @JsonAlias({"refPrice", "reference_price"})
        public Double referencePrice;
        @JsonAlias({"takeProfitPct", "take_profit"})
        public Double takeProfit;
        @JsonAlias({"stopLossPct", "stop_loss"})
        public Double stopLoss;
        @JsonAlias("max_hold_days")
        public Integer maxHoldDays;
        @JsonAlias("cooldownDays")
        public Integer cooldownDays;
        @JsonProperty("cooldown_until")
        public JsonNode cooldownUntil;
        public Double confidence;
        @JsonAlias("rationale")
        public String reasoning;
        @JsonAlias({"risks", "risk_flags"})
        public List<String> riskFlags;
        @JsonAlias({"needsHumanReview", "manual_review"})
        public Boolean manualReview;
    }
}
