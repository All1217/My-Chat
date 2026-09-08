package com.mychat.apps.market;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mychat.apps.market.entity.po.MarketForecast;
import com.mychat.apps.market.entity.vo.KlinePointVO;
import com.mychat.apps.market.entity.vo.MarketForecastVO;
import com.mychat.apps.market.entity.vo.MarketQuoteVO;
import com.mychat.apps.market.mapper.MarketForecastMapper;
import com.mychat.job.AsyncJobService;
import com.mychat.vo.AsyncJobVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 股市分析业务：同步取行情，异步提交 Agent 预测。
 */
@Slf4j
@Service
public class MarketForecastService {

    public static final String JOB_TYPE = "market_forecast";

    private static final TypeReference<List<KlinePointVO>> KLINE_LIST = new TypeReference<>() {
    };

    private final MarketQuoteClient quoteClient;
    private final MarketForecastMapper forecastMapper;
    private final AsyncJobService asyncJobService;
    private final ChatClient chatClient;
    /** Spring Boot 4 容器里是 Jackson 3，这里自建 Jackson 2 Mapper 序列化 K 线 JSON。 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 注入行情、落库、任务窗口和无工具 Agent。 */
    public MarketForecastService(
            MarketQuoteClient quoteClient,
            MarketForecastMapper forecastMapper,
            AsyncJobService asyncJobService,
            @Qualifier("agentWorkflowChatClient") ChatClient chatClient) {
        this.quoteClient = quoteClient;
        this.forecastMapper = forecastMapper;
        this.asyncJobService = asyncJobService;
        this.chatClient = chatClient;
    }

    /** 只拉行情，不写库。 */
    public MarketQuoteVO quote(String symbol, String range) {
        return quoteClient.fetch(symbol, range);
    }

    /**
     * 再拉一次行情固化快照，提交 market_forecast 任务后立刻返回。
     */
    public MarketForecastVO submit(String symbol, String range) {
        MarketQuoteVO quote = quoteClient.fetch(symbol, range);
        MarketForecast row = new MarketForecast();
        row.setId(UUID.randomUUID().toString());
        row.setSymbol(quote.getSymbol());
        row.setMarket(quote.getMarket());
        row.setRangeKey(quote.getRangeKey());
        row.setName(quote.getName());
        row.setHistoryJson(writeJson(quote.getHistory()));
        row.setStatus(MarketForecast.STATUS_PENDING);
        forecastMapper.insert(row);

        String title = "预测走势：" + (StringUtils.hasText(quote.getName()) ? quote.getName() : quote.getSymbol());
        AsyncJobVO job = asyncJobService.submit(JOB_TYPE, title, row.getId(), null, false);
        row.setJobId(job.getId());
        forecastMapper.updateById(row);
        return toVo(forecastMapper.selectById(row.getId()));
    }

    /** 按 id 查询快照与预测。 */
    public MarketForecastVO get(String id) {
        if (!StringUtils.hasText(id)) {
            throw new IllegalArgumentException("id 不能为空");
        }
        MarketForecast row = forecastMapper.selectById(id.trim());
        if (row == null) {
            throw new IllegalArgumentException("预测记录不存在");
        }
        return toVo(row);
    }

    /**
     * JobHandler 调用：读历史快照，让 Agent 填未来收盘价。
     */
    public void runForecast(String forecastId) throws Exception {
        if (!StringUtils.hasText(forecastId)) {
            throw new IllegalArgumentException("market_forecast 缺少 refId");
        }
        MarketForecast row = forecastMapper.selectById(forecastId.trim());
        if (row == null) {
            throw new IllegalArgumentException("预测记录不存在");
        }
        try {
            row.setStatus(MarketForecast.STATUS_RUNNING);
            forecastMapper.updateById(row);

            List<KlinePointVO> history = readHistory(row.getHistoryJson());
            if (history.isEmpty()) {
                throw new IllegalStateException("历史 K 线为空");
            }
            int bars = MarketCalendar.forecastBars(history.size());
            LocalDate last = LocalDate.parse(history.get(history.size() - 1).getDate());
            List<String> futureDates = MarketCalendar.nextTradingDays(last, bars);
            ForecastOutput raw = callAgent(row, history, futureDates);
            List<KlinePointVO> forecast = alignPoints(futureDates, raw);
            String summary = raw != null && StringUtils.hasText(raw.summary())
                    ? clip(raw.summary(), 500)
                    : "情景推演已完成（非投资建议）";

            row.setForecastJson(writeJson(forecast));
            row.setSummary(summary);
            row.setStatus(MarketForecast.STATUS_SUCCEEDED);
            row.setErrorMessage(null);
            forecastMapper.updateById(row);
        } catch (Exception e) {
            markFailed(forecastId.trim(), e.getMessage());
            throw e;
        }
    }

    /** 调用无工具 ChatClient，按 prompt 解析 JSON。 */
    private ForecastOutput callAgent(MarketForecast row, List<KlinePointVO> history, List<String> futureDates) {
        String sampled = formatSample(history);
        String dates = String.join("\n", futureDates);
        String user = """
                股票：%s（%s %s）
                历史收盘价抽样（date,close）：
                %s

                请为下列未来交易日各给出一个 close，量级贴近历史，不要编造未列出的日期：
                %s

                只输出 JSON：{"summary":"一两句中文情景说明","points":[{"date":"YYYY-MM-DD","close":123.45}]}
                points 必须与上列日期一一对应。这是走势情景推演，不是投资建议。
                """.formatted(
                StringUtils.hasText(row.getName()) ? row.getName() : row.getSymbol(),
                row.getMarket(),
                row.getSymbol(),
                sampled,
                dates);

        String text = chatClient.prompt()
                .system("""
                        你是量化走势助手。根据历史收盘价对指定未来交易日做情景推演。
                        禁止输出 Markdown 或额外说明。不要声称能保证收益。
                        """)
                .user(user)
                .call()
                .content();
        ForecastOutput raw = parseForecast(text);
        if (raw == null || raw.points() == null || raw.points().isEmpty()) {
            throw new IllegalStateException("模型未返回有效预测点");
        }
        return raw;
    }

    /** 从模型文本里抽出 ForecastOutput，不走 .entity 以免附带超大 JSON Schema。 */
    ForecastOutput parseForecast(String text) {
        if (!StringUtils.hasText(text)) {
            throw new IllegalStateException("模型未返回有效预测点");
        }
        String json = text.trim();
        int start = json.indexOf('{');
        int end = json.lastIndexOf('}');
        if (start >= 0 && end > start) {
            json = json.substring(start, end + 1);
        }
        try {
            return objectMapper.readValue(json, ForecastOutput.class);
        } catch (Exception e) {
            throw new IllegalStateException("模型未返回有效预测点");
        }
    }

    /** 按预定日期对齐模型输出，缺省沿用上一收盘。 */
    static List<KlinePointVO> alignPoints(List<String> futureDates, ForecastOutput raw) {
        List<ForecastPoint> points = raw.points() == null ? List.of() : raw.points();
        List<KlinePointVO> out = new ArrayList<>();
        Double lastClose = points.isEmpty() ? null : points.get(0).close();
        for (int i = 0; i < futureDates.size(); i++) {
            String date = futureDates.get(i);
            Double close = null;
            if (i < points.size() && points.get(i).close() != null) {
                close = points.get(i).close();
            } else {
                for (ForecastPoint p : points) {
                    if (date.equals(p.date()) && p.close() != null) {
                        close = p.close();
                        break;
                    }
                }
            }
            if (close == null) {
                close = lastClose;
            }
            if (close == null) {
                throw new IllegalStateException("预测点缺少收盘价");
            }
            lastClose = close;
            KlinePointVO vo = new KlinePointVO();
            vo.setDate(date);
            vo.setClose(close);
            out.add(vo);
        }
        return out;
    }

    /** 历史过长时等距抽样，控制 prompt 体积。 */
    static String formatSample(List<KlinePointVO> history) {
        int n = history.size();
        int step = n <= 80 ? 1 : (int) Math.ceil(n / 80.0);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i += step) {
            KlinePointVO p = history.get(i);
            if (p.getDate() == null || p.getClose() == null) {
                continue;
            }
            sb.append(p.getDate()).append(',').append(p.getClose()).append('\n');
        }
        KlinePointVO last = history.get(n - 1);
        if ((n - 1) % step != 0 && last.getDate() != null && last.getClose() != null) {
            sb.append(last.getDate()).append(',').append(last.getClose()).append('\n');
        }
        return sb.toString().trim();
    }

    /** 失败时写入功能表，再让调度器把 async_job 标 FAILED。 */
    private void markFailed(String id, String message) {
        MarketForecast row = forecastMapper.selectById(id);
        if (row == null) {
            return;
        }
        row.setStatus(MarketForecast.STATUS_FAILED);
        row.setErrorMessage(clip(message == null ? "预测失败" : message, 1000));
        forecastMapper.updateById(row);
    }

    /** 行转 VO，顺带用历史 K 线填统计。 */
    MarketForecastVO toVo(MarketForecast row) {
        MarketForecastVO vo = new MarketForecastVO();
        vo.setId(row.getId());
        vo.setJobId(row.getJobId());
        vo.setStatus(row.getStatus());
        vo.setSymbol(row.getSymbol());
        vo.setMarket(row.getMarket());
        vo.setRangeKey(row.getRangeKey());
        vo.setName(row.getName());
        vo.setSummary(row.getSummary());
        vo.setErrorMessage(row.getErrorMessage());
        List<KlinePointVO> history = readHistory(row.getHistoryJson());
        vo.setHistory(history);
        if (!history.isEmpty()) {
            MarketQuoteVO tmp = new MarketQuoteVO();
            tmp.setHistory(history);
            MarketQuoteClient.fillStats(tmp);
            vo.setLastPrice(tmp.getLastPrice());
            vo.setChangePct(tmp.getChangePct());
            vo.setHigh(tmp.getHigh());
            vo.setLow(tmp.getLow());
            vo.setVolume(tmp.getVolume());
        }
        vo.setForecast(readHistory(row.getForecastJson()));
        return vo;
    }

    private List<KlinePointVO> readHistory(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            List<KlinePointVO> list = objectMapper.readValue(json, KLINE_LIST);
            return list == null ? List.of() : list;
        } catch (Exception e) {
            log.warn("解析 K 线 JSON 失败: {}", e.getMessage());
            return List.of();
        }
    }

    private String writeJson(List<KlinePointVO> points) {
        try {
            return objectMapper.writeValueAsString(points);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("序列化 K 线失败", e);
        }
    }

    private static String clip(String text, int max) {
        String t = text.trim();
        return t.length() <= max ? t : t.substring(0, max);
    }

    /** Agent 结构化输出。 */
    public record ForecastOutput(String summary, List<ForecastPoint> points) {
    }

    /** 单个预测点。 */
    public record ForecastPoint(String date, Double close) {
    }
}
