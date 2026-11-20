package com.mychat.apps.papertrade.research;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mychat.apps.market.EtfPremiumClient;
import com.mychat.apps.market.MarketDipIndicators;
import com.mychat.apps.market.MarketQuoteClient;
import com.mychat.apps.market.entity.vo.MarketQuoteVO;
import com.mychat.apps.papertrade.PaperTradeProperties;
import com.mychat.apps.papertrade.agent.PaperAgentRuntimeAdapter;
import com.mychat.entity.po.AgentRun;
import com.mychat.agent.persistence.AgentRunRepository;
import com.mychat.apps.papertrade.workflow.PaperWorkflowTrace;
import com.mychat.apps.papertrade.workflow.PaperWorkflowStep;
import com.mychat.apps.papertrade.workflow.PaperRunLifecycle;
import com.mychat.apps.papertrade.guardrail.PaperTradeGuardrails;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.mychat.apps.papertrade.agent.ResearchCardBatch;
import com.mychat.apps.papertrade.ledger.PaperTradeService;
import com.mychat.apps.papertrade.risk.PaperSymbolFacts;
import com.mychat.apps.papertrade.risk.PaperSymbolGate;
import com.mychat.apps.papertrade.research.entity.po.PaperResearchNote;
import com.mychat.apps.papertrade.research.entity.po.PaperResearchRun;
import com.mychat.apps.papertrade.trader.PaperStrategyService;
import com.mychat.apps.papertrade.trader.entity.po.PaperStrategy;
import com.mychat.apps.papertrade.trader.entity.vo.PaperAiSubmitVO;
import com.mychat.apps.papertrade.research.entity.vo.PaperResearchNoteVO;
import com.mychat.apps.papertrade.research.mapper.PaperResearchNoteMapper;
import com.mychat.apps.papertrade.research.mapper.PaperResearchRunMapper;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 搜集员：一小批行情和公告交给编排器，结果累积进卡片表。不下单。
 */
@Slf4j
@Service
public class PaperResearchService {

    public static final String JOB_TYPE = "paper_trade_research";
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_PAGES = 8;
    private static final int STRATEGY_CLIP = 6000;
    private static final String TIER_A = "A_ONLY";
    private static final String TIER_A_WEB = "A_PLUS_WEB";
    /** qualify 外呼并发上限。 */
    private static final int QUALIFY_PARALLELISM = 6;
    /** 全轮爬虫 fetch 共享预算（非每票）。 */
    private static final int CRAWL_BUDGET_PER_ROUND = 4;

    private final PaperResearchRunMapper runMapper;
    private final PaperResearchNoteMapper noteMapper;
    private final PaperStoryFeed feed;
    private final PaperMacroFeed macroFeed;
    private final PaperCrawlerClient crawlerClient;
    private final MarketQuoteClient quoteClient;
    private final EtfPremiumClient premiumClient;
    private final PaperSymbolGate symbolGate;
    private final PaperTradeService tradeService;
    private final PaperTradeProperties properties;
    private final PaperAgentRuntimeAdapter agentRuntime;
    private final AsyncJobService asyncJobService;
    private final PaperStrategyService strategyService;
    private final PaperRunLifecycle lifecycle;
    private final PaperTradeGuardrails guardrails;
    private final AgentRunRepository agentRunRepository;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    /** Agent 写工具 upsertResearchCards 按 runId 取本批材料。 */
    private final ConcurrentHashMap<String, List<Material>> materialByRun = new ConcurrentHashMap<>();

    public PaperResearchService(PaperResearchRunMapper runMapper,
                                PaperResearchNoteMapper noteMapper,
                                PaperStoryFeed feed,
                                PaperMacroFeed macroFeed,
                                PaperCrawlerClient crawlerClient,
                                MarketQuoteClient quoteClient,
                                EtfPremiumClient premiumClient,
                                PaperSymbolGate symbolGate,
                                PaperTradeService tradeService,
                                PaperTradeProperties properties,
                                PaperAgentRuntimeAdapter agentRuntime,
                                AsyncJobService asyncJobService,
                                PaperStrategyService strategyService,
                                PaperRunLifecycle lifecycle,
                                PaperTradeGuardrails guardrails,
                                AgentRunRepository agentRunRepository) {
        this.runMapper = runMapper;
        this.noteMapper = noteMapper;
        this.feed = feed;
        this.macroFeed = macroFeed;
        this.crawlerClient = crawlerClient;
        this.quoteClient = quoteClient;
        this.premiumClient = premiumClient;
        this.symbolGate = symbolGate;
        this.tradeService = tradeService;
        this.properties = properties;
        this.agentRuntime = agentRuntime;
        this.asyncJobService = asyncJobService;
        this.strategyService = strategyService;
        this.lifecycle = lifecycle;
        this.guardrails = guardrails;
        this.agentRunRepository = agentRunRepository;
    }

    /** 提交一轮搜集，立刻返回；拒绝重叠活动轮次。 */
    @Transactional
    public PaperAiSubmitVO submitRun() {
        lifecycle.requireNoActive(runMapper, q -> q.in(
                PaperResearchRun::getStatus,
                PaperRunLifecycle.activeStatuses(
                        PaperResearchRun.STATUS_PENDING, PaperResearchRun.STATUS_RUNNING)));
        PaperResearchRun run = new PaperResearchRun();
        run.setId(UUID.randomUUID().toString().replace("-", ""));
        run.setStatus(PaperResearchRun.STATUS_PENDING);
        run.setScreenedCount(0);
        run.setKeptCount(0);
        runMapper.insert(run);
        AsyncJobVO job = asyncJobService.submit(JOB_TYPE, "模拟炒股搜集一轮", run.getId(), null, true);
        run.setJobId(job.getId());
        runMapper.updateById(run);
        PaperAiSubmitVO vo = new PaperAiSubmitVO();
        vo.setRunId(run.getId());
        vo.setJobId(job.getId());
        vo.setStatus(run.getStatus());
        return vo;
    }

    /** 对已有卡片补资料。payload 带代码，整轮搜集仍传 null。 */
    @Transactional
    public PaperAiSubmitVO submitRefresh(String symbol) {
        if (!StringUtils.hasText(symbol)) {
            throw new IllegalArgumentException("symbol 不能为空");
        }
        String code = symbol.trim().toUpperCase(Locale.ROOT);
        if (noteMapper.selectById(code) == null) {
            throw new IllegalArgumentException("研究卡片不存在");
        }
        lifecycle.requireNoActive(runMapper, q -> q.in(
                PaperResearchRun::getStatus,
                PaperRunLifecycle.activeStatuses(
                        PaperResearchRun.STATUS_PENDING, PaperResearchRun.STATUS_RUNNING)));
        PaperResearchRun run = new PaperResearchRun();
        run.setId(UUID.randomUUID().toString().replace("-", ""));
        run.setStatus(PaperResearchRun.STATUS_PENDING);
        run.setScreenedCount(0);
        run.setKeptCount(0);
        runMapper.insert(run);
        String payload;
        try {
            payload = objectMapper.writeValueAsString(Map.of("symbol", code));
        } catch (Exception e) {
            throw new IllegalStateException("无法提交更新");
        }
        AsyncJobVO job = asyncJobService.submit(JOB_TYPE, "模拟炒股更新卡片 " + code, run.getId(), payload, true);
        run.setJobId(job.getId());
        runMapper.updateById(run);
        PaperAiSubmitVO vo = new PaperAiSubmitVO();
        vo.setRunId(run.getId());
        vo.setJobId(job.getId());
        vo.setStatus(run.getStatus());
        return vo;
    }

    /** 从任务 payload 取出单票代码；空 payload 表示整轮搜集。 */
    static String refreshSymbol(String payload) {
        if (!StringUtils.hasText(payload)) {
            return null;
        }
        try {
            JsonNode node = new ObjectMapper().readTree(payload);
            String symbol = node.path("symbol").asText("");
            if (!StringUtils.hasText(symbol)) {
                throw new IllegalArgumentException("搜集任务 payload 缺少 symbol");
            }
            return symbol.trim().toUpperCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("搜集任务 payload 无法解析");
        }
    }

    /** 后台执行。材料不足或无有效卡片时成功结束，kept=0。symbol 非空时只补这一张。 */
    public void run(String runId, String symbol) throws Exception {
        PaperResearchRun run = runMapper.selectById(runId);
        if (run == null) {
            throw new IllegalArgumentException("搜集轮次不存在");
        }
        PaperWorkflowTrace trace = new PaperWorkflowTrace();
        boolean marked = lifecycle.tryMarkRunning(runMapper, u -> u
                .eq(PaperResearchRun::getId, runId)
                .eq(PaperResearchRun::getStatus, PaperResearchRun.STATUS_PENDING)
                .set(PaperResearchRun::getStatus, PaperResearchRun.STATUS_RUNNING));
        if (!marked) {
            log.info("搜集轮次已非 PENDING，跳过重复执行 runId={} status={}", runId, run.getStatus());
            return;
        }
        run.setStatus(PaperResearchRun.STATUS_RUNNING);
        try {
            // START_RUN 已完成条件进入 RUNNING
            trace.ok(PaperWorkflowStep.START_RUN, "jobId=" + run.getJobId());

            // DISCOVER：整轮翻页筛选；单票只抓这一张已有卡片
            List<Material> batch = StringUtils.hasText(symbol)
                    ? List.of(qualifyRefresh(symbol))
                    : screen();
            run.setScreenedCount(batch.size());
            trace.ok(PaperWorkflowStep.DISCOVER_CANDIDATES, "picked=" + batch.size());
            trace.ok(PaperWorkflowStep.VALIDATE_MARKET_DATA, "qualified=" + batch.size());
            if (batch.isEmpty()) {
                run.setFrameworkTrace(trace.toJson(objectMapper));
                finish(run, 0, null, null);
                trace.ok(PaperWorkflowStep.FINISH_RUN, "empty-batch");
                return;
            }

            // 供 Agent 写工具 upsertResearchCards 取本批材料
            materialByRun.put(run.getId(), batch);
            try {
                PaperAgentRuntimeAdapter.Outcome outcome = agentRuntime.research(
                        prompt(batch, run.getId(), StringUtils.hasText(symbol)),
                        run.getId(),
                        run.getJobId());
                trace.bindAgentRun(outcome.agentRunId());
                trace.ok(PaperWorkflowStep.AI_COLLECT_AND_ANALYZE, "agentRunId=" + outcome.agentRunId());

                List<Card> cards = parseTypedCards(outcome.answer());
                Set<String> pool = new HashSet<>();
                for (Material m : batch) {
                    pool.add(m.symbol());
                }
                cards = guardrails.validateCards(cards, pool);
                trace.ok(PaperWorkflowStep.VALIDATE_CARDS, "cards=" + cards.size());
                // 工具已落库则跳过；否则 Java 兜底并告警
                long upserted = noteMapper.selectCount(new LambdaQueryWrapper<PaperResearchNote>()
                        .eq(PaperResearchNote::getRunId, run.getId()));
                int kept;
                if (upserted > 0) {
                    kept = countKeptForRun(run.getId());
                    trace.ok(PaperWorkflowStep.UPSERT_NOTES,
                            "via-tool upserted≈" + upserted + " kept=" + kept);
                } else if (cards.isEmpty()) {
                    kept = 0;
                } else {
                    log.warn("搜集员未走 upsertResearchCards，Java 兜底落库 runId={}", run.getId());
                    int newlyKept = upsert(run.getId(), batch, cards);
                    kept = countKeptForRun(run.getId());
                    trace.ok(PaperWorkflowStep.UPSERT_NOTES,
                            "fallback newlyKept=" + newlyKept + " kept=" + kept);
                }
                run.setFrameworkTrace(mergeTrace(trace, outcome.traceJson()));
                finish(run, kept, outcome.answer(), null);
                trace.ok(PaperWorkflowStep.FINISH_RUN, "kept=" + kept);
            } finally {
                materialByRun.remove(run.getId());
            }
        } catch (Exception e) {
            trace.fail(PaperWorkflowStep.FINISH_RUN, e.getMessage());
            run.setFrameworkTrace(trace.toJson(objectMapper));
            run.setStatus(PaperResearchRun.STATUS_FAILED);
            run.setErrorMessage(clip(e.getMessage(), 1000));
            run.setFinishedAt(LocalDateTime.now());
            runMapper.updateById(run);
            throw e;
        }
    }

    /** 把 agentRunId/domainSteps 与适配器轨迹合并进 framework_trace。 */
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

    /** 已留下的卡片，按更新时间倒序。 */
    public List<PaperResearchNoteVO> listKept(int limit) {
        int n = limit <= 0 ? 40 : Math.min(limit, 100);
        List<PaperResearchNote> rows = noteMapper.selectList(new LambdaQueryWrapper<PaperResearchNote>()
                .eq(PaperResearchNote::getKept, true)
                .orderByDesc(PaperResearchNote::getUpdatedAt)
                .last("LIMIT " + n));
        List<PaperResearchNoteVO> out = new ArrayList<>();
        for (PaperResearchNote row : rows) {
            PaperResearchNoteVO vo = new PaperResearchNoteVO();
            vo.setSymbol(row.getSymbol());
            vo.setName(row.getName());
            vo.setStory(row.getStory());
            vo.setRisks(row.getRisks());
            vo.setSourceTitles(titles(row.getSourcesJson()));
            vo.setFitScore(row.getFitScore());
            vo.setEvidenceScore(row.getEvidenceScore());
            vo.setSignalTags(row.getSignalTags());
            vo.setSourceTier(row.getSourceTier());
            vo.setValueTier(row.getValueTier());
            if (row.getUpdatedAt() != null) {
                vo.setUpdatedAt(row.getUpdatedAt().format(TS));
            }
            out.add(vo);
        }
        return out;
    }

    /** 轮询用的状态。 */
    public PaperResearchRun getRun(String id) {
        PaperResearchRun run = runMapper.selectById(id);
        if (run == null) {
            throw new IllegalArgumentException("搜集轮次不存在");
        }
        run.setAgentRunId(resolveAgentRunId(id));
        return run;
    }

    /** 通过 business_ref 反查最近一次 agent_run。 */
    private String resolveAgentRunId(String businessRef) {
        List<AgentRun> runs = agentRunRepository.search(
                null, null, businessRef, null, null, 1);
        return runs.isEmpty() ? null : runs.get(0).getId();
    }

    private void finish(PaperResearchRun run, int kept, String raw, String error) {
        run.setKeptCount(kept);
        run.setStatus(error == null ? PaperResearchRun.STATUS_SUCCEEDED : PaperResearchRun.STATUS_FAILED);
        run.setErrorMessage(error);
        run.setFinishedAt(LocalDateTime.now());
        runMapper.updateById(run);
        if (raw != null) {
            log.info("搜集完成 runId={} screened={} kept={}", run.getId(), run.getScreenedCount(), kept);
        }
    }

    /** 本轮 runId 下 kept=true 的卡片数（写入 kept_count，与落库条数区分）。 */
    private int countKeptForRun(String runId) {
        if (!StringUtils.hasText(runId)) {
            return 0;
        }
        long n = noteMapper.selectCount(new LambdaQueryWrapper<PaperResearchNote>()
                .eq(PaperResearchNote::getRunId, runId.trim())
                .eq(PaperResearchNote::getKept, true));
        return (int) Math.min(n, Integer.MAX_VALUE);
    }

    /** 翻页直到凑够一批，或翻完上限。 */
    private List<Material> screen() {
        int batchSize = Math.max(1, properties.getResearchBatchSize());
        BigDecimal cash = tradeService.requireAccount().getCash();
        Set<String> recent = recentSymbols();
        List<PaperStoryFeed.ListRow> picked = new ArrayList<>();
        for (int page = 1; page <= MAX_PAGES && picked.size() < batchSize; page++) {
            List<PaperStoryFeed.ListRow> rows = feed.page(page);
            if (rows.isEmpty()) {
                break;
            }
            for (PaperStoryFeed.ListRow row : rows) {
                if (picked.size() >= batchSize) {
                    break;
                }
                String code = row.code().toUpperCase(Locale.ROOT);
                if (recent.contains(code) || PaperResearchFilters.looksSt(row.name())) {
                    continue;
                }
                if (!PaperResearchFilters.affordable(row.price(), cash)
                        || PaperResearchFilters.limitUpHint(code, row.changePct())) {
                    continue;
                }
                picked.add(row);
            }
        }
        return qualify(picked);
    }

    private Set<String> recentSymbols() {
        LocalDateTime now = LocalDateTime.now();
        List<PaperResearchNote> notes = noteMapper.selectList(new LambdaQueryWrapper<PaperResearchNote>());
        Set<String> recent = new HashSet<>();
        for (PaperResearchNote note : notes) {
            if (PaperResearchFilters.recentlyTouched(note.getUpdatedAt(), properties.getResearchSkipDays(), now)) {
                recent.add(note.getSymbol().toUpperCase(Locale.ROOT));
            }
        }
        return recent;
    }

    /**
     * 日 K 闸门和相对低点；接口信源 + 爬虫正文软合并。
     * 按票并行外呼（并发 {@link #QUALIFY_PARALLELISM}），结果保持 picked 顺序。
     */
    private List<Material> qualify(List<PaperStoryFeed.ListRow> picked) {
        long started = System.nanoTime();
        if (picked == null || picked.isEmpty()) {
            log.info("qualify 完成 qualifyMs=0 picked=0 qualified=0 crawlUsed=0");
            return List.of();
        }
        AtomicInteger quoteFailures = new AtomicInteger();
        AtomicInteger crawlBudget = new AtomicInteger(CRAWL_BUDGET_PER_ROUND);
        AtomicInteger crawlUsed = new AtomicInteger();
        Material[] slots = new Material[picked.size()];
        ExecutorService exec = Executors.newFixedThreadPool(QUALIFY_PARALLELISM);
        try {
            List<Future<?>> futures = new ArrayList<>(picked.size());
            for (int i = 0; i < picked.size(); i++) {
                final int idx = i;
                final PaperStoryFeed.ListRow row = picked.get(i);
                futures.add(exec.submit(() -> {
                    try {
                        slots[idx] = qualifyOne(row, crawlBudget, crawlUsed);
                    } catch (Exception e) {
                        quoteFailures.incrementAndGet();
                        log.debug("搜集剔除 {}: {}", row.code(), e.getMessage());
                    }
                }));
            }
            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("qualify 被中断", e);
                } catch (ExecutionException e) {
                    throw new IllegalStateException("qualify 并行失败", e.getCause());
                }
            }
        } finally {
            exec.shutdown();
            try {
                if (!exec.awaitTermination(3, TimeUnit.MINUTES)) {
                    exec.shutdownNow();
                }
            } catch (InterruptedException e) {
                exec.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        requireAnyQuote(picked.size(), quoteFailures.get());
        List<Material> out = new ArrayList<>();
        for (Material m : slots) {
            if (m != null) {
                out.add(m);
            }
        }
        long qualifyMs = (System.nanoTime() - started) / 1_000_000L;
        log.info("qualify 完成 qualifyMs={} picked={} qualified={} crawlUsed={}",
                qualifyMs, picked.size(), out.size(), crawlUsed.get());
        return out;
    }

    /** 单票 qualify；溢价 Session 每票自建，避免共享 HashMap。闸门未过返回 null。 */
    private Material qualifyOne(PaperStoryFeed.ListRow row,
                                AtomicInteger crawlBudget,
                                AtomicInteger crawlUsed) {
        MarketQuoteVO quote = quoteClient.fetch(row.code(), "1Y");
        PaperSymbolFacts facts = symbolGate.analyze(quote);
        if (!passesGate(facts)) {
            return null;
        }
        MarketDipIndicators.Snapshot snap = MarketDipIndicators.from(quote);
        if (!relativelyLow(snap)) {
            return null;
        }
        return composeMaterial(row.code(), row.name(), facts, snap, crawlBudget, crawlUsed);
    }

    /** 单票补资料：不要求相对低位、成交额和上市天数。硬伤口直接失败，不改原卡片。 */
    private Material qualifyRefresh(String symbol) {
        String code = symbol.trim().toUpperCase(Locale.ROOT);
        PaperResearchNote note = noteMapper.selectById(code);
        if (note == null) {
            throw new IllegalArgumentException("研究卡片不存在");
        }
        // 行情失败由 quoteClient 抛出，本轮记失败，卡片保持原样
        MarketQuoteVO quote = quoteClient.fetch(code, "1Y");
        PaperSymbolFacts facts = symbolGate.analyze(quote);
        String block = hardBlockReason(facts);
        if (block != null) {
            throw new IllegalStateException(block);
        }
        MarketDipIndicators.Snapshot snap = MarketDipIndicators.from(quote);
        return composeMaterial(code, note.getName(), facts, snap,
                new AtomicInteger(CRAWL_BUDGET_PER_ROUND), new AtomicInteger());
    }

    /** ST、停牌、涨跌停不写卡片。返回 null 表示可以继续。 */
    static String hardBlockReason(PaperSymbolFacts facts) {
        if (facts == null) {
            return "无行情，未更新卡片";
        }
        if (facts.st()) {
            return "ST，未更新卡片";
        }
        if (facts.suspended()) {
            return "停牌，未更新卡片";
        }
        if (facts.limitUp() || facts.limitDown()) {
            return "涨跌停，未更新卡片";
        }
        return null;
    }

    /** 公告、新闻、板块、爬虫和溢价。整轮与单票共用。 */
    private Material composeMaterial(String code,
                                     String fallbackName,
                                     PaperSymbolFacts facts,
                                     MarketDipIndicators.Snapshot snap,
                                     AtomicInteger crawlBudget,
                                     AtomicInteger crawlUsed) {
        String name = facts.name() == null ? fallbackName : facts.name();
        // 公告、新闻、板块失败只跳过，不中断这一票
        List<PaperStoryFeed.SourceItem> sources = new ArrayList<>();
        try {
            mergeSources(sources, feed.announcements(code));
        } catch (Exception e) {
            log.debug("公告接口跳过 {}: {}", code, e.getMessage());
        }
        try {
            mergeSources(sources, feed.news(code));
        } catch (Exception e) {
            log.debug("新闻接口跳过 {}: {}", code, e.getMessage());
        }
        try {
            mergeSources(sources, macroFeed.sectorBriefs(name));
        } catch (Exception e) {
            log.debug("板块检索跳过 {}: {}", code, e.getMessage());
        }
        enrichWithCrawler(sources, crawlBudget, crawlUsed);
        String tier = sources.stream().anyMatch(s -> "CRAWL".equals(s.kind()))
                ? TIER_A_WEB : TIER_A;
        Double premium = null;
        if (facts.etf()) {
            EtfPremiumClient.PremiumQuote pq =
                    premiumClient.newSession().lookup(code, snap.lastPrice());
            if (pq != null) {
                premium = pq.getPremiumPct();
            }
        }
        String symbol = StringUtils.hasText(facts.symbol()) ? facts.symbol() : code;
        return new Material(symbol.toUpperCase(Locale.ROOT),
                name, snap, facts, premium, sources, tier);
    }

    /**
     * 全轮共享爬虫预算：成功抢到名额才 fetch；用尽则只保留标题/URL。
     *
     * @return 本票实际发起的爬虫次数（含失败）
     */
    private int enrichWithCrawler(List<PaperStoryFeed.SourceItem> sources,
                                  AtomicInteger crawlBudget,
                                  AtomicInteger crawlUsed) {
        if (sources == null || sources.isEmpty()) {
            return 0;
        }
        List<PaperStoryFeed.SourceItem> crawled = new ArrayList<>();
        int attempted = 0;
        for (PaperStoryFeed.SourceItem s : sources) {
            if (s == null || !StringUtils.hasText(s.url())) {
                continue;
            }
            if (!tryConsumeCrawlBudget(crawlBudget)) {
                break;
            }
            attempted++;
            crawlUsed.incrementAndGet();
            crawlerClient.fetchText(s.url()).ifPresent(text -> crawled.add(
                    new PaperStoryFeed.SourceItem(
                            "CRAWL",
                            clip((s.title() == null ? "" : s.title()) + " | " + text, 500),
                            s.url(),
                            s.publishedAt())));
        }
        mergeSources(sources, crawled);
        return attempted;
    }

    /** CAS 扣减全轮爬虫预算。 */
    static boolean tryConsumeCrawlBudget(AtomicInteger crawlBudget) {
        while (true) {
            int left = crawlBudget.get();
            if (left <= 0) {
                return false;
            }
            if (crawlBudget.compareAndSet(left, left - 1)) {
                return true;
            }
        }
    }

    /** 按 URL/标题去重追加。 */
    static void mergeSources(List<PaperStoryFeed.SourceItem> into, List<PaperStoryFeed.SourceItem> add) {
        if (add == null || add.isEmpty()) {
            return;
        }
        Set<String> seen = new LinkedHashSet<>();
        for (PaperStoryFeed.SourceItem s : into) {
            seen.add(sourceKey(s));
        }
        for (PaperStoryFeed.SourceItem s : add) {
            if (s == null || !StringUtils.hasText(s.title())) {
                continue;
            }
            if (seen.add(sourceKey(s))) {
                into.add(s);
            }
        }
    }

    private static String sourceKey(PaperStoryFeed.SourceItem s) {
        if (StringUtils.hasText(s.url())) {
            return "u:" + s.url().trim().toLowerCase(Locale.ROOT);
        }
        return "t:" + s.title().trim().toLowerCase(Locale.ROOT);
    }

    /** 本批候选都因行情失败被剔除时，不能记成成功。闸门筛空仍允许留下 0。 */
    static void requireAnyQuote(int picked, int quoteFailures) {
        if (picked > 0 && quoteFailures >= picked) {
            throw new IllegalStateException("本批行情全部失败");
        }
    }

    private boolean passesGate(PaperSymbolFacts facts) {
        if (facts.st() || facts.suspended() || facts.limitUp() || facts.limitDown()) {
            return false;
        }
        int minBars = facts.etf() ? properties.getMinListedBarsEtf() : properties.getMinListedBarsStock();
        if (facts.barCount() < minBars) {
            return false;
        }
        double minAmount = facts.etf() ? properties.getMinAvgAmountEtf() : properties.getMinAvgAmountStock();
        return facts.avgAmount20() != null && facts.avgAmount20() >= minAmount;
    }

    /** 相对低点：低于 MA20 或 60 日回撤 ≤ -8%（与交易员策略口径一致）。 */
    static boolean relativelyLow(MarketDipIndicators.Snapshot snap) {
        if (snap == null) {
            return false;
        }
        boolean belowMa = snap.vsMa20Pct() != null && snap.vsMa20Pct() < 0;
        boolean drawn = snap.drawdown60Pct() != null && snap.drawdown60Pct() <= -8;
        return belowMa || drawn;
    }

    /**
     * tier=1 须相对低点 + 企稳；否则降为 2。
     * 与交易员候选上下文中的 vsMa20Pct / stabilized 对齐。
     */
    static void alignTier1WithStabilizeGates(List<Card> cards, List<Material> batch) {
        if (cards == null || cards.isEmpty() || batch == null || batch.isEmpty()) {
            return;
        }
        Map<String, Material> bySymbol = new HashMap<>();
        for (Material m : batch) {
            if (m != null && StringUtils.hasText(m.symbol())) {
                bySymbol.put(m.symbol().trim().toUpperCase(Locale.ROOT), m);
            }
        }
        for (Card card : cards) {
            if (card == null || !Boolean.TRUE.equals(card.kept) || card.valueTier == null || card.valueTier != 1) {
                continue;
            }
            if (!StringUtils.hasText(card.symbol)) {
                continue;
            }
            Material m = bySymbol.get(card.symbol.trim().toUpperCase(Locale.ROOT));
            if (m == null) {
                continue;
            }
            boolean low = relativelyLow(m.snap());
            boolean stab = m.facts() != null && m.facts().stabilized();
            if (low && stab) {
                continue;
            }
            card.valueTier = 2;
            String reason = !low && !stab
                    ? "TIER1_WITHOUT_DIP_OR_STABILIZED"
                    : (!low ? "TIER1_WITHOUT_RELATIVE_LOW" : "TIER1_WITHOUT_STABILIZED");
            if (!StringUtils.hasText(card.rejectReason)) {
                card.rejectReason = reason;
            } else if (!card.rejectReason.contains(reason)) {
                card.rejectReason = clip(card.rejectReason + ";" + reason, 500);
            }
        }
    }

    private String prompt(List<Material> batch, String runId, boolean refreshOne) throws Exception {
        ArrayNode arr = objectMapper.createArrayNode();
        for (Material m : batch) {
            ObjectNode n = arr.addObject();
            n.put("symbol", m.symbol());
            n.put("name", m.name());
            n.put("sourceTier", m.sourceTier());
            put(n, "lastPrice", m.snap().lastPrice());
            put(n, "changePct", m.snap().changePct());
            put(n, "vsMa20Pct", m.snap().vsMa20Pct());
            put(n, "drawdown60Pct", m.snap().drawdown60Pct());
            put(n, "avgAmount20", m.facts().avgAmount20());
            put(n, "premiumPct", m.premiumPct());
            n.put("stabilized", m.facts() != null && m.facts().stabilized());
            n.put("relativelyLow", relativelyLow(m.snap()));
            ArrayNode sources = n.putArray("sources");
            for (PaperStoryFeed.SourceItem s : m.sources()) {
                ObjectNode src = sources.addObject();
                src.put("kind", s.kind() == null ? "" : s.kind());
                src.put("title", s.title() == null ? "" : s.title());
                src.put("url", s.url() == null ? "" : s.url());
            }
        }
        return """
                你是模拟炒股的信息搜集员，不下单。
                候选已含行情闸门结果，以及接口公告/新闻与爬虫正文（若成功）。
                可按需再调 paper-strategy、paper-market、paper-research-feed、mcp-crawler；
                web-search 是否可用取决于全局 Exa 开关。禁止编造未给出的 URL。
                终局优先调用 upsertResearchCards(runId, cardsJson) 落库；同时输出紧凑 ResearchCardBatch JSON（cards[]）供兜底。
                本轮 runId=%s。
                %s每项含 symbol,name,kept,story,risks,rejectReason,fitScore,evidenceScore,signalTags,evidenceRefs,valueTier。
                story≤80字；fitScore/evidenceScore 为 0~1；
                valueTier 只能是 1（应该买入）、2（继续观察）、3（移出票仓），禁止输出 0（已持仓由成交回写）。
                标 1（必须同时满足）：kept=true；relativelyLow=true（vsMa20Pct<0 或 drawdown60Pct≤-8）；
                stabilized=true（收阳或收盘>昨收）；fit/evidence 建议≥0.7；story 须分别点明「相对低点」与「企稳」。
                缺相对低点或缺企稳 → 只能标 2，禁止标 1。
                标 2：主题沾边但时机/证据未齐；标 3：论点失效或 kept=false。
                evidenceRefs 必须是字符串数组（标题或 URL），禁止对象；不可凭空生成。
                只能使用候选列表中的代码；证据不足则 kept=false 且 valueTier=3。
                候选：
                %s
                """.formatted(runId, refreshOne
                ? """
                这是指定代码补资料。低于均线、回撤是入场偏好，不能单独作为 kept=false 或价值等级 3 的理由；
                但标 valueTier=1 仍须 relativelyLow 与 stabilized 同时为 true。
                成交额、涨跌幅、资金流向只能照抄候选 JSON，方向不能改。
                材料已齐，不要再调用 getQuote 或 getSymbolFacts。
                """
                : "", objectMapper.writeValueAsString(arr));
    }

    /** 从最终答复或各步观察里取出 JSON 数组。操盘手也用来读编排结果。 */
    public static String firstJson(String answer, String observations) {
        if (hasArray(answer)) {
            return answer;
        }
        if (hasArray(observations)) {
            return observations;
        }
        return answer == null ? "" : answer;
    }

    /** 解析类型化 ResearchCardBatch，并兼容历史裸数组。 */
    static List<Card> parseTypedCards(String text) throws Exception {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        ObjectMapper mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        String json = text.trim();
        if (json.startsWith("{")) {
            ResearchCardBatch batch = mapper.readValue(json, ResearchCardBatch.class);
            List<Card> cards = new ArrayList<>();
            for (ResearchCardBatch.ResearchCardDecision item : batch.cards()) {
                Card card = new Card();
                card.symbol = item.symbol();
                card.name = item.name();
                card.kept = item.kept();
                card.story = item.story();
                card.risks = item.risks() == null || item.risks().isEmpty()
                        ? null : String.join("；", item.risks());
                card.rejectReason = item.rejectReason();
                card.fitScore = item.fitScore();
                card.evidenceScore = item.evidenceScore();
                card.signalTags = item.signalTags();
                card.valueTier = item.valueTier();
                cards.add(card);
            }
            return cards;
        }
        return parseCards(json);
    }

    /** 解析搜集员输出。失败返回空列表，由调用方决定是否记失败。 */
    static List<Card> parseCards(String text) throws Exception {
        if (!hasArray(text)) {
            return List.of();
        }
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper.readValue(text.substring(start, end + 1), new TypeReference<List<Card>>() {
        });
    }

    /** 同一代码只留第一条。大小写和首尾空白视为同一代码。 */
    static List<Card> firstCards(List<Card> cards) {
        if (cards == null || cards.isEmpty()) {
            return List.of();
        }
        List<Card> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Card card : cards) {
            if (card == null || !StringUtils.hasText(card.symbol)) {
                continue;
            }
            String symbol = card.symbol.trim().toUpperCase(Locale.ROOT);
            if (seen.add(symbol)) {
                out.add(card);
            }
        }
        return out;
    }

    private int upsert(String runId, List<Material> batch, List<Card> cards) throws Exception {
        // tier=1 必须相对低点+企稳，与交易员闸门对齐
        alignTier1WithStabilizeGates(cards, batch);
        Map<String, Material> bySymbol = new HashMap<>();
        for (Material m : batch) {
            bySymbol.put(m.symbol(), m);
        }
        int newlyKept = 0;
        int limit = Math.max(1, properties.getResearchKeepLimit());
        for (Card card : firstCards(cards)) {
            if (!StringUtils.hasText(card.symbol)) {
                continue;
            }
            String symbol = card.symbol.trim().toUpperCase(Locale.ROOT);
            Material material = bySymbol.get(symbol);
            if (material == null) {
                continue;
            }
            PaperResearchNote existing = noteMapper.selectById(symbol);
            boolean wasKept = existing != null && Boolean.TRUE.equals(existing.getKept());
            boolean keep = Boolean.TRUE.equals(card.kept);
            String reject = card.rejectReason;
            // 评分缺失则强制不留下，避免空分卡片进入操盘手视图
            BigDecimal fit = score(card.fitScore);
            BigDecimal evidence = score(card.evidenceScore);
            if (fit == null || evidence == null) {
                keep = false;
                if (!StringUtils.hasText(reject)) {
                    reject = "MISSING_SCORE";
                }
            }
            if (keep && !wasKept) {
                if (newlyKept >= limit) {
                    keep = false;
                    reject = "BATCH_CAP";
                } else {
                    newlyKept++;
                }
            }
            PaperResearchNote note = existing == null ? new PaperResearchNote() : existing;
            note.setSymbol(symbol);
            note.setName(StringUtils.hasText(card.name) ? card.name : material.name());
            note.setQuoteSummary(quoteSummary(material));
            note.setStory(clip(card.story, 2000));
            note.setRisks(clip(card.risks, 1000));
            note.setKept(keep);
            note.setRejectReason(clip(reject, 500));
            note.setSourcesJson(objectMapper.writeValueAsString(material.sources()));
            note.setFitScore(fit);
            note.setEvidenceScore(evidence);
            note.setSignalTags(clip(tagsJson(card.signalTags), 500));
            note.setSourceTier(material.sourceTier());
            note.setRunId(runId);
            // 已持仓(0)不覆盖；未留下强制 3；缺省 2
            Integer priorTier = existing == null ? null : existing.getValueTier();
            if (priorTier != null && priorTier == 0) {
                note.setValueTier(0);
            } else {
                note.setValueTier(resolveResearchValueTier(card, keep));
            }
            // AI 新建非自选；更新保留原 watchlist
            if (existing == null) {
                note.setWatchlist(false);
                noteMapper.insert(note);
            } else {
                noteMapper.updateById(note);
            }
        }
        return newlyKept;
    }

    /** 搜集员输出 → 落库等级（1/2/3）。 */
    static int resolveResearchValueTier(Card card, boolean keep) {
        if (!keep) {
            return 3;
        }
        Integer raw = card == null ? null : card.valueTier;
        if (raw == null) {
            return 2;
        }
        if (raw == 1 || raw == 2 || raw == 3) {
            return raw;
        }
        return 2;
    }

    /** 分析员/工具：调价值等级，仅 1/2/3；禁止改 0。标 1 须相对低点+企稳，否则降为 2。 */
    public Map<String, Object> updateValueTierByTool(String symbol, Integer tier) {
        if (!StringUtils.hasText(symbol)) {
            throw new IllegalArgumentException("symbol 不能为空");
        }
        if (tier == null || (tier != 1 && tier != 2 && tier != 3)) {
            throw new IllegalArgumentException("tier 只能是 1、2 或 3");
        }
        String code = symbol.trim().toUpperCase(Locale.ROOT);
        PaperResearchNote note = noteMapper.selectById(code);
        if (note == null) {
            throw new IllegalArgumentException("研究卡片不存在");
        }
        if (note.getValueTier() != null && note.getValueTier() == 0) {
            throw new IllegalArgumentException("已持仓(0)不可通过工具改等级");
        }
        int from = note.getValueTier() == null ? 2 : note.getValueTier();
        int to = tier;
        String demoteReason = null;
        if (to == 1) {
            demoteReason = tier1DemoteReason(code);
            if (demoteReason != null) {
                to = 2;
            }
        }
        note.setValueTier(to);
        if (to == 3) {
            note.setKept(false);
        } else if (to == 1 || to == 2) {
            note.setKept(true);
        }
        noteMapper.updateById(note);
        Map<String, Object> out = new HashMap<>();
        out.put("ok", true);
        out.put("symbol", code);
        out.put("from", from);
        out.put("to", to);
        if (demoteReason != null) {
            out.put("demotedFrom", 1);
            out.put("demoteReason", demoteReason);
        }
        return out;
    }

    /** 现价不满足相对低点或企稳时返回降级原因，否则 null。 */
    private String tier1DemoteReason(String symbol) {
        try {
            MarketQuoteVO quote = quoteClient.fetch(symbol, "1Y");
            PaperSymbolFacts facts = symbolGate.analyze(quote);
            MarketDipIndicators.Snapshot snap = MarketDipIndicators.from(quote);
            boolean low = relativelyLow(snap);
            boolean stab = facts != null && facts.stabilized();
            if (low && stab) {
                return null;
            }
            if (!low && !stab) {
                return "TIER1_WITHOUT_DIP_OR_STABILIZED";
            }
            return !low ? "TIER1_WITHOUT_RELATIVE_LOW" : "TIER1_WITHOUT_STABILIZED";
        } catch (Exception e) {
            log.warn("tier1 闸门复检失败 symbol={}: {}", symbol, e.getMessage());
            return "TIER1_GATE_CHECK_FAILED";
        }
    }

    /** 工具：按 runId 材料 + cardsJson 落库（Agent 可调；无材料则失败）。 */
    public Map<String, Object> upsertCardsFromTool(String runId, String cardsJson) throws Exception {
        List<Material> batch = materialByRun.get(runId);
        if (batch == null || batch.isEmpty()) {
            throw new IllegalStateException("本轮材料未就绪，无法工具落库");
        }
        List<Card> cards = parseTypedCards(cardsJson);
        Set<String> pool = new HashSet<>();
        for (Material m : batch) {
            pool.add(m.symbol());
        }
        cards = guardrails.validateCards(cards, pool);
        int kept = upsert(runId, batch, cards);
        return Map.of("ok", true, "kept", kept, "cards", cards.size());
    }

    /** BUY/SELL 成交后回写价值等级；无卡片则跳过。 */
    public void applyFillValueTier(String symbol, String side) {
        if (!StringUtils.hasText(symbol) || !StringUtils.hasText(side)) {
            return;
        }
        PaperResearchNote note = noteMapper.selectById(symbol.trim().toUpperCase(Locale.ROOT));
        if (note == null) {
            return;
        }
        String action = side.trim().toUpperCase(Locale.ROOT);
        if ("BUY".equals(action)) {
            note.setValueTier(0);
        } else if ("SELL".equals(action)) {
            note.setValueTier(2);
        } else {
            return;
        }
        noteMapper.updateById(note);
    }

    /** 非持仓买入是否允许：卡片 kept 且 valueTier=1。无卡片则不允许。 */
    public boolean isBuyEligibleNote(String symbol) {
        if (!StringUtils.hasText(symbol)) {
            return false;
        }
        PaperResearchNote note = noteMapper.selectById(symbol.trim().toUpperCase(Locale.ROOT));
        return note != null
                && Boolean.TRUE.equals(note.getKept())
                && note.getValueTier() != null
                && note.getValueTier() == 1;
    }

    private String tagsJson(Object signalTags) throws Exception {
        if (signalTags == null) {
            return null;
        }
        if (signalTags instanceof String s) {
            return StringUtils.hasText(s) ? s : null;
        }
        return objectMapper.writeValueAsString(signalTags);
    }

    private static BigDecimal score(Double v) {
        if (v == null || !Double.isFinite(v)) {
            return null;
        }
        double clamped = Math.max(0d, Math.min(1d, v));
        return BigDecimal.valueOf(clamped).setScale(4, RoundingMode.HALF_UP);
    }

    private String quoteSummary(Material material) throws Exception {
        ObjectNode n = objectMapper.createObjectNode();
        put(n, "lastPrice", material.snap().lastPrice());
        put(n, "changePct", material.snap().changePct());
        put(n, "vsMa20Pct", material.snap().vsMa20Pct());
        put(n, "drawdown60Pct", material.snap().drawdown60Pct());
        put(n, "avgAmount20", material.facts().avgAmount20());
        put(n, "premiumPct", material.premiumPct());
        n.put("sourceTier", material.sourceTier());
        return n.toString();
    }

    private String titles(String sourcesJson) {
        if (!StringUtils.hasText(sourcesJson)) {
            return "";
        }
        try {
            List<PaperStoryFeed.SourceItem> items = objectMapper.readValue(sourcesJson,
                    new TypeReference<List<PaperStoryFeed.SourceItem>>() {
                    });
            List<String> titles = new ArrayList<>();
            for (PaperStoryFeed.SourceItem item : items) {
                if (StringUtils.hasText(item.title())) {
                    titles.add(item.title());
                }
            }
            return String.join("；", titles);
        } catch (Exception e) {
            return "";
        }
    }

    private static void put(ObjectNode n, String field, Double v) {
        if (v == null || !Double.isFinite(v)) {
            n.putNull(field);
        } else {
            n.put(field, v);
        }
    }

    private static boolean hasArray(String text) {
        return text != null && text.indexOf('[') >= 0 && text.lastIndexOf(']') > text.indexOf('[');
    }

    private static String clip(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max);
    }

    /** 本轮候选材料（包内可见，供闸门对齐单测构造）。 */
    record Material(String symbol, String name, MarketDipIndicators.Snapshot snap,
                    PaperSymbolFacts facts, Double premiumPct,
                    List<PaperStoryFeed.SourceItem> sources, String sourceTier) {
    }

    /** 搜集员 JSON 的一行。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Card {
        public String symbol;
        public String name;
        public Boolean kept;
        public String story;
        public String risks;
        public String rejectReason;
        @JsonAlias({"fit_score"})
        public Double fitScore;
        @JsonAlias({"evidence_score"})
        public Double evidenceScore;
        @JsonAlias({"signal_tags"})
        public Object signalTags;
        @JsonAlias({"value_tier"})
        public Integer valueTier;
    }
}
