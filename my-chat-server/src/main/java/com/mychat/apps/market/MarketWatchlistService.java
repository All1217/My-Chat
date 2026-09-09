package com.mychat.apps.market;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mychat.apps.market.entity.vo.MarketQuoteVO;
import com.mychat.apps.market.entity.vo.MarketStrategyVO;
import com.mychat.apps.market.entity.vo.MarketWatchlistAlertVO;
import com.mychat.apps.market.entity.vo.MarketWatchlistVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 自选股落盘与按日缓存的抄底提醒。 */
@Slf4j
@Service
public class MarketWatchlistService {

    static final int MAX_SYMBOLS = 8;
    private static final String RANGE = "1Y";
    private static final String NONE_REASON = "暂无加仓提醒";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MarketStrategyProperties properties;
    private final MarketStrategyService strategyService;
    private final MarketQuoteClient quoteClient;
    private final ChatClient chatClient;

    /** 注入文件路径、策略、行情与无工具 Agent。 */
    public MarketWatchlistService(
            MarketStrategyProperties properties,
            MarketStrategyService strategyService,
            MarketQuoteClient quoteClient,
            @Qualifier("agentWorkflowChatClient") ChatClient chatClient) {
        this.properties = properties;
        this.strategyService = strategyService;
        this.quoteClient = quoteClient;
        this.chatClient = chatClient;
    }

    /** 读取自选列表，不调模型。 */
    public MarketWatchlistVO load() {
        return toListVo(readFile());
    }

    /** 规范化后覆盖保存，并清空当日提醒缓存。 */
    public MarketWatchlistVO save(List<String> rawSymbols) {
        WatchlistFile file = readFile();
        file.symbols = normalize(rawSymbols);
        clearCache(file);
        writeFile(file);
        return toListVo(file);
    }

    /** 当天提醒：无自选/无策略不调模型；指纹未变则用缓存。 */
    public MarketWatchlistAlertVO alert() {
        return alertOn(LocalDate.now());
    }

    /** 可注入日期以便单测缓存命中。 */
    MarketWatchlistAlertVO alertOn(LocalDate today) {
        WatchlistFile file = readFile();
        if (file.symbols == null || file.symbols.isEmpty()) {
            return alertVo("", "", "请先添加自选股", false);
        }
        MarketStrategyVO strategy = strategyService.load();
        String strategyText = strategy.getStrategyText() == null ? "" : strategy.getStrategyText().trim();
        if (!StringUtils.hasText(strategyText)) {
            return alertVo("", "", "请先保存投资策略", false);
        }
        String dateKey = today.toString();
        String strategyFp = fingerprint(strategyText);
        String symbolsKey = String.join("|", file.symbols);
        if (dateKey.equals(file.cacheDate)
                && strategyFp.equals(file.strategyFingerprint)
                && symbolsKey.equals(file.symbolsKey)) {
            return alertVo(file.pickSymbol, file.pickName, file.reason, true);
        }
        List<MarketDipIndicators.Snapshot> snapshots = new ArrayList<>();
        for (String symbol : file.symbols) {
            try {
                MarketQuoteVO quote = quoteClient.fetch(symbol, RANGE);
                snapshots.add(MarketDipIndicators.from(quote));
            } catch (IllegalArgumentException e) {
                log.warn("自选行情失败 symbol={}: {}", symbol, e.getMessage());
            }
        }
        if (snapshots.isEmpty()) {
            throw new IllegalArgumentException("自选股暂无足够行情，请稍后重试");
        }
        Pick pick = pickFromAgent(strategyText, snapshots);
        file.cacheDate = dateKey;
        file.strategyFingerprint = strategyFp;
        file.symbolsKey = symbolsKey;
        file.pickSymbol = pick.symbol;
        file.pickName = pick.name;
        file.reason = pick.reason;
        writeFile(file);
        return alertVo(file.pickSymbol, file.pickName, file.reason, false);
    }

    /** 去空白、解析代码、去重，最多 8 只。 */
    List<String> normalize(List<String> rawSymbols) {
        Set<String> unique = new LinkedHashSet<>();
        if (rawSymbols == null) {
            return new ArrayList<>();
        }
        for (String raw : rawSymbols) {
            if (!StringUtils.hasText(raw)) {
                continue;
            }
            String symbol = MarketSymbolParser.parse(raw.trim()).symbol();
            if (unique.contains(symbol)) {
                continue;
            }
            unique.add(symbol);
            if (unique.size() >= MAX_SYMBOLS) {
                break;
            }
        }
        return new ArrayList<>(unique);
    }

    /** 一次调用模型，允许 pick 为空。 */
    Pick pickFromAgent(String strategyText, List<MarketDipIndicators.Snapshot> snapshots) {
        StringBuilder rows = new StringBuilder();
        Set<String> allowed = new LinkedHashSet<>();
        for (MarketDipIndicators.Snapshot snap : snapshots) {
            allowed.add(snap.symbol());
            rows.append("- ")
                    .append(snap.symbol()).append(" ").append(snap.name())
                    .append(" 最新价=").append(num(snap.lastPrice()))
                    .append(" 日涨跌%=").append(num(snap.changePct()))
                    .append(" 20日回撤%=").append(num(snap.drawdown20Pct()))
                    .append(" 60日回撤%=").append(num(snap.drawdown60Pct()))
                    .append(" 相对MA20%=").append(num(snap.vsMa20Pct()))
                    .append(" 相对MA60%=").append(num(snap.vsMa60Pct()))
                    .append(" RSI14=").append(num(snap.rsi14()))
                    .append(" 量比=").append(num(snap.volumeRatio()))
                    .append(" 连跌天数=").append(snap.downDays() == null ? "-" : snap.downDays())
                    .append('\n');
        }
        String user = """
                用户投资策略：
                %s

                自选股日 K 指标（不要编造未给出的数字）：
                %s
                请最多挑选一只当前更适合酌情加仓的股票；若都不合适，pickSymbol 留空。
                只输出 JSON：{"pickSymbol":"代码或空字符串","reason":"一两句中文，须引用上面的数字"}
                这不是投资建议。
                """.formatted(strategyText, rows);
        String text = chatClient.prompt()
                .system("""
                        你是仓位助手。根据日线回撤、均线、RSI、量比判断是否存在回踩加仓机会。
                        可以一只都不选。禁止保证收益。不要输出 Markdown。
                        """)
                .user(user)
                .call()
                .content();
        return parsePick(text, snapshots, allowed);
    }

    /** 抽出 JSON；非法代码视为空选。 */
    Pick parsePick(String text, List<MarketDipIndicators.Snapshot> snapshots, Set<String> allowed) {
        if (!StringUtils.hasText(text)) {
            return new Pick("", "", NONE_REASON);
        }
        String json = text.trim();
        int start = json.indexOf('{');
        int end = json.lastIndexOf('}');
        if (start >= 0 && end > start) {
            json = json.substring(start, end + 1);
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            String symbol = node.path("pickSymbol").asText("").trim();
            String reason = node.path("reason").asText("").trim();
            if (!StringUtils.hasText(symbol) || !allowed.contains(symbol)) {
                return new Pick("", "", StringUtils.hasText(reason) ? reason : NONE_REASON);
            }
            String name = "";
            for (MarketDipIndicators.Snapshot snap : snapshots) {
                if (symbol.equals(snap.symbol())) {
                    name = snap.name();
                    break;
                }
            }
            if (!StringUtils.hasText(reason)) {
                reason = NONE_REASON;
            }
            return new Pick(symbol, name, reason);
        } catch (Exception e) {
            log.warn("解析抄底提醒失败: {}", e.getMessage());
            return new Pick("", "", NONE_REASON);
        }
    }

    private static String fingerprint(String text) {
        return Integer.toHexString(text.hashCode()) + ":" + text.length();
    }

    private static String num(Double value) {
        return value == null ? "-" : value.toString();
    }

    private static void clearCache(WatchlistFile file) {
        file.cacheDate = "";
        file.strategyFingerprint = "";
        file.symbolsKey = "";
        file.pickSymbol = "";
        file.pickName = "";
        file.reason = "";
    }

    private WatchlistFile readFile() {
        Path path = Path.of(properties.getWatchlistFile());
        if (!Files.isRegularFile(path)) {
            return new WatchlistFile();
        }
        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            if (!StringUtils.hasText(json)) {
                return new WatchlistFile();
            }
            WatchlistFile file = objectMapper.readValue(json, WatchlistFile.class);
            return file == null ? new WatchlistFile() : file;
        } catch (IOException e) {
            log.warn("读取自选文件失败 path={}: {}", path, e.getMessage());
            return new WatchlistFile();
        }
    }

    private void writeFile(WatchlistFile file) {
        Path path = Path.of(properties.getWatchlistFile());
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(file);
            Files.writeString(path, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("写入自选文件失败 path={}: {}", path, e.getMessage());
            throw new IllegalArgumentException("无法保存自选股");
        }
    }

    private static MarketWatchlistVO toListVo(WatchlistFile file) {
        MarketWatchlistVO vo = new MarketWatchlistVO();
        vo.setSymbols(file.symbols == null ? new ArrayList<>() : new ArrayList<>(file.symbols));
        return vo;
    }

    private static MarketWatchlistAlertVO alertVo(String symbol, String name, String reason, boolean cached) {
        MarketWatchlistAlertVO vo = new MarketWatchlistAlertVO();
        vo.setPickSymbol(symbol == null ? "" : symbol);
        vo.setPickName(name == null ? "" : name);
        vo.setReason(StringUtils.hasText(reason) ? reason : NONE_REASON);
        vo.setCached(cached);
        return vo;
    }

    /** 模型挑选结果。 */
    record Pick(String symbol, String name, String reason) {
    }

    /** JSON 落盘结构。 */
    static class WatchlistFile {
        public List<String> symbols = new ArrayList<>();
        public String cacheDate = "";
        public String strategyFingerprint = "";
        public String symbolsKey = "";
        public String pickSymbol = "";
        public String pickName = "";
        public String reason = "";
    }
}
