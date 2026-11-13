package com.mychat.apps.papertrade.research;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mychat.apps.market.EtfPremiumClient;
import com.mychat.apps.market.MarketDipIndicators;
import com.mychat.apps.market.MarketQuoteClient;
import com.mychat.apps.market.entity.vo.MarketQuoteVO;
import com.mychat.apps.papertrade.PaperTradeProperties;
import com.mychat.apps.papertrade.agent.PaperFrameworkCall;
import com.mychat.apps.papertrade.ledger.PaperTradeService;
import com.mychat.apps.papertrade.risk.PaperSymbolFacts;
import com.mychat.apps.papertrade.risk.PaperSymbolGate;
import com.mychat.apps.papertrade.research.entity.po.PaperResearchNote;
import com.mychat.apps.papertrade.research.entity.po.PaperResearchRun;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 搜集员：一小批行情和公告交给编排器，结果累积进卡片表。不下单。
 */
@Slf4j
@Service
public class PaperResearchService {

    public static final String JOB_TYPE = "paper_trade_research";
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_PAGES = 8;

    private final PaperResearchRunMapper runMapper;
    private final PaperResearchNoteMapper noteMapper;
    private final PaperStoryFeed feed;
    private final MarketQuoteClient quoteClient;
    private final EtfPremiumClient premiumClient;
    private final PaperSymbolGate symbolGate;
    private final PaperTradeService tradeService;
    private final PaperTradeProperties properties;
    private final PaperFrameworkCall frameworkCall;
    private final AsyncJobService asyncJobService;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public PaperResearchService(PaperResearchRunMapper runMapper,
                                PaperResearchNoteMapper noteMapper,
                                PaperStoryFeed feed,
                                MarketQuoteClient quoteClient,
                                EtfPremiumClient premiumClient,
                                PaperSymbolGate symbolGate,
                                PaperTradeService tradeService,
                                PaperTradeProperties properties,
                                PaperFrameworkCall frameworkCall,
                                AsyncJobService asyncJobService) {
        this.runMapper = runMapper;
        this.noteMapper = noteMapper;
        this.feed = feed;
        this.quoteClient = quoteClient;
        this.premiumClient = premiumClient;
        this.symbolGate = symbolGate;
        this.tradeService = tradeService;
        this.properties = properties;
        this.frameworkCall = frameworkCall;
        this.asyncJobService = asyncJobService;
    }

    /** 提交一轮搜集，立刻返回。 */
    @Transactional
    public PaperAiSubmitVO submitRun() {
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

    /** 后台执行。材料不足时直接成功，不调用模型。 */
    public void run(String runId) throws Exception {
        PaperResearchRun run = runMapper.selectById(runId);
        if (run == null) {
            throw new IllegalArgumentException("搜集轮次不存在");
        }
        run.setStatus(PaperResearchRun.STATUS_RUNNING);
        runMapper.updateById(run);
        try {
            // 1. 名单：跳过近期整理过的，再按闸门收成一小批
            List<Material> batch = screen();
            run.setScreenedCount(batch.size());
            if (batch.isEmpty()) {
                finish(run, 0, null, null);
                return;
            }
            // 2. 编排器整理，解析后按代码更新
            PaperFrameworkCall.Outcome outcome = frameworkCall.call(prompt(batch), 4);
            run.setFrameworkTrace(outcome.traceJson());
            String text = firstJson(outcome.answer(), outcome.observations());
            if (!hasArray(text)) {
                throw new IllegalStateException("搜集员没有返回 JSON 数组");
            }
            List<Card> cards = parseCards(text);
            int kept = upsert(run.getId(), batch, cards);
            finish(run, kept, outcome.answer(), null);
        } catch (Exception e) {
            run.setStatus(PaperResearchRun.STATUS_FAILED);
            run.setErrorMessage(clip(e.getMessage(), 1000));
            run.setFinishedAt(LocalDateTime.now());
            runMapper.updateById(run);
            throw e;
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
        return run;
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

    /** 日 K 闸门和相对低点。上市天数用一年日 K，月 K 不够 60 根。 */
    private List<Material> qualify(List<PaperStoryFeed.ListRow> picked) {
        EtfPremiumClient.Session premiums = premiumClient.newSession();
        List<Material> out = new ArrayList<>();
        int quoteFailures = 0;
        for (PaperStoryFeed.ListRow row : picked) {
            try {
                MarketQuoteVO quote = quoteClient.fetch(row.code(), "1Y");
                PaperSymbolFacts facts = symbolGate.analyze(quote);
                if (!passesGate(facts)) {
                    continue;
                }
                MarketDipIndicators.Snapshot snap = MarketDipIndicators.from(quote);
                if (!relativelyLow(snap)) {
                    continue;
                }
                List<PaperStoryFeed.SourceItem> sources = new ArrayList<>();
                sources.addAll(feed.announcements(row.code()));
                sources.addAll(feed.news(row.code()));
                Double premium = null;
                if (facts.etf()) {
                    EtfPremiumClient.PremiumQuote pq = premiums.lookup(row.code(), snap.lastPrice());
                    if (pq != null) {
                        premium = pq.getPremiumPct();
                    }
                }
                out.add(new Material(facts.symbol().toUpperCase(Locale.ROOT),
                        facts.name() == null ? row.name() : facts.name(), snap, facts, premium, sources));
            } catch (Exception e) {
                quoteFailures++;
                log.debug("搜集剔除 {}: {}", row.code(), e.getMessage());
            }
        }
        requireAnyQuote(picked.size(), quoteFailures);
        return out;
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

    private static boolean relativelyLow(MarketDipIndicators.Snapshot snap) {
        boolean belowMa = snap.vsMa20Pct() != null && snap.vsMa20Pct() < 0;
        boolean drawn = snap.drawdown60Pct() != null && snap.drawdown60Pct() <= -8;
        return belowMa || drawn;
    }

    private String prompt(List<Material> batch) throws Exception {
        ArrayNode arr = objectMapper.createArrayNode();
        for (Material m : batch) {
            ObjectNode n = arr.addObject();
            n.put("symbol", m.symbol());
            n.put("name", m.name());
            put(n, "lastPrice", m.snap().lastPrice());
            put(n, "changePct", m.snap().changePct());
            put(n, "vsMa20Pct", m.snap().vsMa20Pct());
            put(n, "drawdown60Pct", m.snap().drawdown60Pct());
            put(n, "avgAmount20", m.facts().avgAmount20());
            put(n, "premiumPct", m.premiumPct());
            ArrayNode sources = n.putArray("sources");
            for (PaperStoryFeed.SourceItem s : m.sources()) {
                ObjectNode src = sources.addObject();
                src.put("kind", s.kind());
                src.put("title", s.title());
                if (s.publishedAt() != null) {
                    src.put("publishedAt", s.publishedAt());
                }
            }
        }
        return """
                你是模拟炒股的信息搜集员，不下单。下面是本批候选的行情和已抓到的公告、新闻标题。
                请整理成 JSON 数组，不要 Markdown。每项字段：symbol,name,kept,story,risks,rejectReason。
                kept 为 true 才留给操盘手。故事只能引用本批 sources 的标题或行情数字，没有材料就写证据不足并把 kept 设为 false。
                禁止编造未给出的新闻。不要选择联网搜索以外的新代码。
                材料：
                %s
                """.formatted(objectMapper.writeValueAsString(arr));
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
            note.setRunId(runId);
            if (existing == null) {
                noteMapper.insert(note);
            } else {
                noteMapper.updateById(note);
            }
        }
        return newlyKept;
    }

    private String quoteSummary(Material material) throws Exception {
        ObjectNode n = objectMapper.createObjectNode();
        put(n, "lastPrice", material.snap().lastPrice());
        put(n, "changePct", material.snap().changePct());
        put(n, "vsMa20Pct", material.snap().vsMa20Pct());
        put(n, "drawdown60Pct", material.snap().drawdown60Pct());
        put(n, "avgAmount20", material.facts().avgAmount20());
        put(n, "premiumPct", material.premiumPct());
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

    private record Material(String symbol, String name, MarketDipIndicators.Snapshot snap,
                             PaperSymbolFacts facts, Double premiumPct, List<PaperStoryFeed.SourceItem> sources) {
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
    }
}
