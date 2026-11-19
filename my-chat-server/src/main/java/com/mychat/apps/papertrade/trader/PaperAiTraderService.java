package com.mychat.apps.papertrade.trader;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mychat.apps.papertrade.PaperTradeProperties;
import com.mychat.apps.papertrade.agent.PaperAgentRuntimeAdapter;
import com.mychat.entity.po.AgentRun;
import com.mychat.agent.persistence.AgentRunRepository;
import com.mychat.apps.papertrade.workflow.PaperWorkflowTrace;
import com.mychat.apps.papertrade.workflow.PaperWorkflowStep;
import com.mychat.apps.papertrade.workflow.PaperRunLifecycle;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.mychat.apps.papertrade.agent.TradeDecisionBatch;
import com.mychat.apps.papertrade.ledger.PaperTradeService;
import com.mychat.apps.papertrade.ledger.entity.dto.PaperOrderRequest;
import com.mychat.apps.papertrade.research.PaperResearchService;
import com.mychat.apps.papertrade.risk.PaperGateContext;
import com.mychat.apps.papertrade.risk.PaperPositionExitService;
import com.mychat.apps.papertrade.risk.PaperRiskGuard;
import com.mychat.apps.papertrade.risk.PaperRiskSettingsService;
import com.mychat.apps.papertrade.evaluator.PaperTradeEvaluatorService;
import com.mychat.apps.papertrade.evaluator.entity.po.PaperTradeEvaluatorRun;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
    private final PaperTradeEvaluatorService evaluatorService;
    private final AsyncJobService asyncJobService;
    private final PaperAgentRuntimeAdapter agentRuntime;
    private final PaperRunLifecycle lifecycle;
    private final AgentRunRepository agentRunRepository;
    private final PaperResearchService researchService;
    private final PaperRiskSettingsService riskSettingsService;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    /** Agent 工具已落库决策的 runId。 */
    private final ConcurrentHashMap<String, Boolean> toolWroteDecisions = new ConcurrentHashMap<>();
    /** 本轮首次组上下文时的标的 facts，供工具二次 build 行情抖动时复用。 */
    private final ConcurrentHashMap<String, Map<String, PaperSymbolFacts>> runFactsCache =
            new ConcurrentHashMap<>();

    public PaperAiTraderService(PaperAiRunMapper runMapper,
                                PaperAiDecisionMapper decisionMapper,
                                PaperStrategyService strategyService,
                                PaperAiContextBuilder contextBuilder,
                                PaperRiskGuard riskGuard,
                                PaperTradeService tradeService,
                                PaperTradeProperties properties,
                                PaperPositionExitService exitService,
                                PaperTradeEvaluatorService evaluatorService,
                                AsyncJobService asyncJobService,
                                PaperAgentRuntimeAdapter agentRuntime,
                                PaperRunLifecycle lifecycle,
                                AgentRunRepository agentRunRepository,
                                PaperResearchService researchService,
                                PaperRiskSettingsService riskSettingsService) {
        this.runMapper = runMapper;
        this.decisionMapper = decisionMapper;
        this.strategyService = strategyService;
        this.contextBuilder = contextBuilder;
        this.riskGuard = riskGuard;
        this.tradeService = tradeService;
        this.properties = properties;
        this.exitService = exitService;
        this.evaluatorService = evaluatorService;
        this.asyncJobService = asyncJobService;
        this.agentRuntime = agentRuntime;
        this.lifecycle = lifecycle;
        this.agentRunRepository = agentRunRepository;
        this.researchService = researchService;
        this.riskSettingsService = riskSettingsService;
    }

    /** 提交一轮：立刻返回 runId + jobId；拒绝重叠活动轮次。 */
    @Transactional
    public PaperAiSubmitVO submitRun() {
        lifecycle.requireNoActive(runMapper, q -> q.in(
                PaperAiRun::getStatus,
                PaperRunLifecycle.activeStatuses(
                        PaperAiRun.STATUS_PENDING, PaperAiRun.STATUS_RUNNING)));
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
        boolean marked = lifecycle.tryMarkRunning(runMapper, u -> u
                .eq(PaperAiRun::getId, runId.trim())
                .eq(PaperAiRun::getStatus, PaperAiRun.STATUS_PENDING)
                .set(PaperAiRun::getStatus, PaperAiRun.STATUS_RUNNING));
        if (!marked) {
            log.info("交易轮次已非 PENDING，跳过重复执行 runId={} status={}", runId, run.getStatus());
            return;
        }
        run.setStatus(PaperAiRun.STATUS_RUNNING);
        PaperWorkflowTrace trace = new PaperWorkflowTrace();
        try {
            trace.ok(PaperWorkflowStep.START_RUN, "jobId=" + run.getJobId());
            // 1. 出场先于模型，跌停的持仓本轮不卖
            exitService.scanAndEnqueue(run.getId());
            flushPendingExits(run.getId(), baseGate());
            PaperTradeService.EquityMark mark = tradeService.markEquity();
            tradeService.refreshPeak(mark);
            trace.ok(PaperWorkflowStep.PROCESS_RULE_EXITS, "exits flushed");

            // 2. 组上下文；枯竭时仅唤醒评估员（总结+重置由评估员完成），本轮继续
            PaperAiContextBuilder.BuiltContext ctx = contextBuilder.build();
            if (shouldReset(ctx)) {
                try {
                    evaluatorService.submitRun(PaperTradeEvaluatorRun.TRIGGER_LIQUIDITY_RESET);
                } catch (Exception e) {
                    log.warn("枯竭唤醒评估员失败: {}", e.getMessage());
                }
            }
            run.setContextJson(ctx.contextJson());
            runMapper.updateById(run);
            trace.ok(PaperWorkflowStep.BUILD_CONTEXT, "context ready");
            cacheRunFacts(run.getId(), ctx.facts());
            PaperGateContext gate = baseGate();
            gate.setPositions(ctx.positions());
            gate.setCooldownSymbols(ctx.cooldownSymbols());
            gate.setPremiums(ctx.premiums());
            gate.setFacts(mergeFacts(run.getId(), ctx.facts(), null));
            gate.setBestEtfSymbol(ctx.bestEtfSymbol());
            gate.setCash(ctx.cash());

            // 3. 调模型并逐条过闸门
            PaperStrategy strategy = strategyService.requireDefault();
            PaperAgentRuntimeAdapter.Outcome outcome =
                    callAgent(strategy.getContent(), ctx.contextJson(), run.getId(), run.getJobId());
            String raw = outcome.answer();
            run.setRawResponse(raw);
            trace.bindAgentRun(outcome.agentRunId());
            trace.ok(PaperWorkflowStep.AI_REVIEW_AND_DECIDE, "agentRunId=" + outcome.agentRunId());

            List<RawDecision> parsed = parseTypedDecisions(raw);
            trace.ok(PaperWorkflowStep.VALIDATE_DECISIONS, "decisions=" + parsed.size());
            boolean viaTool = Boolean.TRUE.equals(toolWroteDecisions.remove(run.getId()));
            if (viaTool) {
                // 工具已落库：补跑仍 PENDING 的（未调 placePaperOrder 时）
                List<PaperAiDecision> pending = decisionMapper.selectList(
                        new LambdaQueryWrapper<PaperAiDecision>()
                                .eq(PaperAiDecision::getRunId, run.getId())
                                .eq(PaperAiDecision::getExecStatus, PaperAiDecision.EXEC_PENDING));
                for (PaperAiDecision d : pending) {
                    processDecision(d, gate, PaperOrder.SOURCE_AI);
                    if (PaperAiDecision.EXEC_EXECUTED.equals(d.getExecStatus())) {
                        refreshGateBook(gate);
                    }
                }
            } else {
                if (!parsed.isEmpty()) {
                    log.warn("交易员未走 recordAiDecisions，Java 兜底落库/执行 runId={}", run.getId());
                }
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
            }

            run.setFrameworkTrace(mergeTrace(trace, outcome.traceJson()));
            run.setStatus(PaperAiRun.STATUS_SUCCEEDED);
            run.setFinishedAt(LocalDateTime.now());
            runMapper.updateById(run);
            trace.ok(PaperWorkflowStep.SETTLE_AND_FINISH, viaTool ? "via-tool" : "ok");
        } catch (Exception e) {
            run.setFrameworkTrace(trace.toJson(objectMapper));
            run.setStatus(PaperAiRun.STATUS_FAILED);
            run.setErrorMessage(truncate(e.getMessage(), 1000));
            run.setFinishedAt(LocalDateTime.now());
            runMapper.updateById(run);
            throw e;
        } finally {
            clearRunCaches(run.getId());
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
        gate.setFacts(mergeFacts(d.getRunId(), ctx.facts(), d.getSymbol()));
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

    /** 合并领域步骤与 Agent 轨迹。 */
    private String mergeTrace(PaperWorkflowTrace domain, String agentTraceJson) {
        try {
            var root = objectMapper.readTree(domain.toJson(objectMapper));
            if (agentTraceJson != null && !agentTraceJson.isBlank()) {
                ((com.fasterxml.jackson.databind.node.ObjectNode) root)
                        .set("agent", objectMapper.readTree(agentTraceJson));
            }
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            return domain.toJson(objectMapper);
        }
    }

    private PaperAgentRuntimeAdapter.Outcome callAgent(
            String strategy, String contextJson, String runId, String asyncJobId) {
        String system = strategy + """


                【平台硬约束】
                1. 优先调用 recordAiDecisions(runId, decisionsJson) 落库，再对 buy/sell 调用 placePaperOrder(decisionId)。
                2. 同时可输出 TradeDecisionBatch JSON（decisions[]）供兜底；不要 Markdown。
                3. 禁止编造未在上下文给出的行情数字；可引用 candidates/positions 中的字段。
                4. hold/flat 不下单；buy/sell 才进入执行闸门。动作请用小写或大写均可。
                5. 数量用 quantity（A 股 100 股整数倍）。不足 1 手会被拒绝，不要向上凑整。
                6. 买入必须给出止损/止盈比例小数（如 0.03=3%），不是绝对价格。字段可用 stop_loss/take_profit 或 stopLoss/takeProfit。
                7. 已持有的标的不要再买。连续止损暂停、回撤收紧时遵守上下文里的限制。
                8. 只能买卖 candidates 与 positions 里出现的代码。不要搜索新标的，不要使用知识库。
                9. 买入仅考虑 valueTier=1（应该买入）的候选；valueTier=0 为已持仓只可卖出。
                """;
        String user = """
                当前账户与行情上下文（JSON）：
                %s

                本轮 runId=%s。请给出本轮决策。止损止盈与参考价均为比例小数或价格数字字段，snake_case 与 camelCase 均可：
                action,symbol,name,quantity,capital_ratio|capitalRatio,reference_price|referencePrice,
                take_profit|takeProfit,stop_loss|stopLoss,max_hold_days|maxHoldDays,
                confidence,reasoning|rationale,risk_flags|riskFlags,manual_review|manualReview
                """.formatted(contextJson, runId);
        return agentRuntime.trader(system + "\n" + user, runId, asyncJobId);
    }

    /** 工具：决策批次落库（不自动下单；由 placePaperOrder 或 Java 兜底执行）。 */
    public Map<String, Object> recordDecisionsFromTool(String runId, String decisionsJson) {
        if (!StringUtils.hasText(runId)) {
            throw new IllegalArgumentException("runId 不能为空");
        }
        PaperAiRun run = runMapper.selectById(runId.trim());
        if (run == null) {
            throw new IllegalArgumentException("交易轮次不存在");
        }
        List<RawDecision> parsed = parseTypedDecisions(decisionsJson);
        PaperAiContextBuilder.BuiltContext ctx = contextBuilder.build();
        PaperGateContext gate = baseGate();
        gate.setPositions(ctx.positions());
        gate.setCooldownSymbols(ctx.cooldownSymbols());
        gate.setPremiums(ctx.premiums());
        gate.setFacts(mergeFacts(runId.trim(), ctx.facts(), null));
        gate.setBestEtfSymbol(ctx.bestEtfSymbol());
        gate.setCash(ctx.cash());
        List<String> ids = new ArrayList<>();
        for (RawDecision rd : parsed) {
            PaperAiDecision d = toDecision(runId.trim(), rd);
            decisionMapper.insert(d);
            ids.add(d.getId());
            if (outsidePool(d, ctx.candidateSymbols())) {
                d.setExecStatus(PaperAiDecision.EXEC_SKIPPED);
                d.setSkipReason("NOT_IN_POOL");
                decisionMapper.updateById(d);
                continue;
            }
            // 工具链：落库后由 placePaperOrder 触发；此处仅闸门预检 hold/flat
            if (PaperAiDecision.ACTION_HOLD.equals(d.getAction())
                    || PaperAiDecision.ACTION_FLAT.equals(d.getAction())) {
                d.setExecStatus(PaperAiDecision.EXEC_SKIPPED);
                d.setSkipReason("NO_ORDER");
                decisionMapper.updateById(d);
            }
        }
        toolWroteDecisions.put(runId.trim(), true);
        return Map.of("ok", true, "count", ids.size(), "decisionIds", ids);
    }

    /** 工具：按决策 ID 执行下单（含风控）。 */
    public Map<String, Object> placeFromTool(String decisionId) {
        if (!StringUtils.hasText(decisionId)) {
            throw new IllegalArgumentException("decisionId 不能为空");
        }
        PaperAiDecision d = decisionMapper.selectById(decisionId.trim());
        if (d == null) {
            throw new IllegalArgumentException("决策不存在");
        }
        if (!PaperAiDecision.EXEC_PENDING.equals(d.getExecStatus())) {
            return Map.of("ok", false, "status", d.getExecStatus(),
                    "reason", d.getSkipReason() == null ? "" : d.getSkipReason());
        }
        PaperAiContextBuilder.BuiltContext ctx = contextBuilder.build();
        PaperGateContext gate = baseGate();
        gate.setPositions(ctx.positions());
        gate.setCooldownSymbols(ctx.cooldownSymbols());
        gate.setPremiums(ctx.premiums());
        gate.setFacts(mergeFacts(d.getRunId(), ctx.facts(), d.getSymbol()));
        gate.setBestEtfSymbol(ctx.bestEtfSymbol());
        gate.setCash(ctx.cash());
        processDecision(d, gate, PaperOrder.SOURCE_AI);
        PaperAiDecision latest = decisionMapper.selectById(d.getId());
        return Map.of(
                "ok", true,
                "decisionId", latest.getId(),
                "status", latest.getExecStatus() == null ? "" : latest.getExecStatus(),
                "orderId", latest.getOrderId() == null ? "" : latest.getOrderId(),
                "skipReason", latest.getSkipReason() == null ? "" : latest.getSkipReason());
    }

    /** 买入或卖出的代码必须来自本轮候选（持仓和已留卡片）。 */
    private static boolean outsidePool(PaperAiDecision d, java.util.Set<String> symbols) {
        if (!"BUY".equals(d.getAction()) && !"SELL".equals(d.getAction())) {
            return false;
        }
        String symbol = d.getSymbol() == null ? "" : d.getSymbol().trim().toUpperCase(Locale.ROOT);
        return !StringUtils.hasText(symbol) || symbols == null || !symbols.contains(symbol);
    }

    /** 解析类型化 TradeDecisionBatch，并兼容历史裸数组。 */
    List<RawDecision> parseTypedDecisions(String text) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        String json = text.trim();
        try {
            if (json.startsWith("{") && json.contains("\"decisions\"")) {
                TradeDecisionBatch batch = objectMapper.readValue(json, TradeDecisionBatch.class);
                List<RawDecision> out = new ArrayList<>();
                for (TradeDecisionBatch.TradeDecisionProposal item : batch.decisions()) {
                    RawDecision rd = new RawDecision();
                    rd.action = item.action();
                    rd.symbol = item.symbol();
                    rd.name = item.name();
                    rd.quantity = item.quantity();
                    rd.capitalRatio = item.capitalRatio() == null ? null : item.capitalRatio().doubleValue();
                    rd.referencePrice = item.referencePrice() == null ? null : item.referencePrice().doubleValue();
                    rd.takeProfit = item.takeProfit() == null ? null : item.takeProfit().doubleValue();
                    rd.stopLoss = item.stopLoss() == null ? null : item.stopLoss().doubleValue();
                    rd.maxHoldDays = item.maxHoldDays();
                    rd.cooldownDays = item.cooldownDays();
                    rd.confidence = item.confidence();
                    rd.reasoning = item.reasoning();
                    rd.riskFlags = item.riskFlags();
                    rd.manualReview = item.manualReview();
                    out.add(rd);
                }
                return out;
            }
        } catch (Exception exception) {
            throw new IllegalStateException("无法解析类型化交易决策: " + exception.getMessage());
        }
        return parseDecisions(json);
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
        Boolean autoFlag = riskSettingsService.requireSettings().getAutoExecute();
        boolean auto = autoFlag == null || Boolean.TRUE.equals(autoFlag);
        if (auto && !needReview) {
            place(d, source);
        } else {
            decisionMapper.updateById(d);
        }
    }

    private String applyGate(PaperAiDecision d, PaperGateContext gate) {
        if (PaperAiDecision.ACTION_BUY.equals(d.getAction())) {
            // 硬门槛：非持仓买入必须来自价值等级 1
            boolean alreadyHeld = gate != null && gate.getPositions() != null
                    && gate.getPositions().stream()
                    .anyMatch(p -> p.getSymbol() != null
                            && p.getSymbol().equalsIgnoreCase(d.getSymbol()));
            if (!alreadyHeld && !researchService.isBuyEligibleNote(d.getSymbol())) {
                return "VALUE_TIER_NOT_BUY";
            }
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
            // BUY→0；整仓 SELL→2（部分卖出仍持仓则保持 0）
            if (PaperAiDecision.ACTION_BUY.equals(d.getAction())) {
                researchService.applyFillValueTier(d.getSymbol(), "BUY");
            } else if (PaperAiDecision.ACTION_SELL.equals(d.getAction())) {
                boolean stillHeld = tradeService.listPositionEntities().stream()
                        .anyMatch(p -> p.getSymbol() != null
                                && p.getSymbol().equalsIgnoreCase(d.getSymbol()));
                if (!stillHeld) {
                    researchService.applyFillValueTier(d.getSymbol(), "SELL");
                }
            }
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
        var risk = riskSettingsService.requireSettings();
        double tight = risk.getRiskBudgetTightPct() == null || risk.getRiskBudgetTightPct() <= 0
                ? properties.getRiskBudgetTightPct() : risk.getRiskBudgetTightPct();
        double normal = risk.getRiskBudgetPct() == null || risk.getRiskBudgetPct() <= 0
                ? properties.getRiskBudgetPct() : risk.getRiskBudgetPct();
        double budget = gate.isDrawdownRestricted() ? tight : normal;
        gate.setRiskBudgetPct(budget <= 0 ? 0.02 : budget);
        gate.setPremiums(Map.of());
        gate.setFacts(Map.of());
        return gate;
    }

    /** 写入本轮首次可用的标的 facts。 */
    private void cacheRunFacts(String runId, Map<String, PaperSymbolFacts> facts) {
        if (!StringUtils.hasText(runId) || facts == null || facts.isEmpty()) {
            return;
        }
        runFactsCache.put(runId.trim(), new ConcurrentHashMap<>(facts));
    }

    /**
     * 合并 live facts 与本轮缓存：live 覆盖同名标的；live 缺失时保留缓存。
     * forSymbol 非空且仅靠缓存命中时打 WARN。
     */
    private Map<String, PaperSymbolFacts> mergeFacts(String runId,
                                                     Map<String, PaperSymbolFacts> live,
                                                     String forSymbol) {
        Map<String, PaperSymbolFacts> merged = new LinkedHashMap<>();
        Map<String, PaperSymbolFacts> cached = StringUtils.hasText(runId)
                ? runFactsCache.get(runId.trim()) : null;
        if (cached != null && !cached.isEmpty()) {
            merged.putAll(cached);
        }
        if (live != null && !live.isEmpty()) {
            merged.putAll(live);
            if (StringUtils.hasText(runId)) {
                runFactsCache.compute(runId.trim(), (key, old) -> {
                    ConcurrentHashMap<String, PaperSymbolFacts> next =
                            old == null ? new ConcurrentHashMap<>() : new ConcurrentHashMap<>(old);
                    next.putAll(live);
                    return next;
                });
            }
        }
        if (StringUtils.hasText(forSymbol)) {
            String symbol = forSymbol.trim().toUpperCase(Locale.ROOT);
            boolean liveHit = live != null && live.containsKey(symbol);
            boolean cacheHit = cached != null && cached.containsKey(symbol);
            if (!liveHit && cacheHit) {
                log.warn("下单复用本轮缓存 facts symbol={} runId={}", symbol, runId);
            }
        }
        return merged;
    }

    private void clearRunCaches(String runId) {
        if (!StringUtils.hasText(runId)) {
            return;
        }
        String id = runId.trim();
        runFactsCache.remove(id);
        toolWroteDecisions.remove(id);
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
        var risk = riskSettingsService.requireSettings();
        double threshold = risk.getReviewConfidenceThreshold() == null
                ? properties.getReviewConfidenceThreshold()
                : risk.getReviewConfidenceThreshold();
        return confidence.doubleValue() < threshold;
    }

    private String resolveAgentRunId(String businessRef) {
        List<AgentRun> runs = agentRunRepository.search(
                null, null, businessRef, null, null, 1);
        return runs.isEmpty() ? null : runs.get(0).getId();
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
        vo.setAgentRunId(resolveAgentRunId(row.getId()));
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
        @JsonAlias({"qty", "shares"})
        public Integer quantity;
        @JsonAlias({"allocationPct", "capital_ratio"})
        public Double capitalRatio;
        @JsonAlias({"refPrice", "reference_price", "price"})
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
        @JsonAlias({"rationale", "reason"})
        public String reasoning;
        @JsonAlias({"risks", "risk_flags"})
        public List<String> riskFlags;
        @JsonAlias({"needsHumanReview", "manual_review"})
        public Boolean manualReview;
    }
}
