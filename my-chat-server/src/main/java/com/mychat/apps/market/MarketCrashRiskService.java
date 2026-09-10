package com.mychat.apps.market;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mychat.apps.market.entity.vo.MarketCrashRiskVO;
import com.mychat.job.AsyncJobService;
import com.mychat.service.agent.worker.SearchWorker;
import com.mychat.vo.AsyncJobVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.Locale;

/** 美股大回撤情景预警：按 ISO 周缓存，跨周才联网搜索。 */
@Slf4j
@Service
public class MarketCrashRiskService {

    public static final String JOB_TYPE = "market_crash_risk";
    static final String STATUS_RUNNING = "RUNNING";
    static final String STATUS_SUCCEEDED = "SUCCEEDED";
    static final String STATUS_FAILED = "FAILED";

    private static final WeekFields ISO_WEEK = WeekFields.ISO;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MarketStrategyProperties properties;
    private final SearchWorker searchWorker;
    private final AsyncJobService asyncJobService;

    /** 注入落盘路径、联网搜索 Worker 与任务窗口。 */
    public MarketCrashRiskService(
            MarketStrategyProperties properties,
            SearchWorker searchWorker,
            AsyncJobService asyncJobService) {
        this.properties = properties;
        this.searchWorker = searchWorker;
        this.asyncJobService = asyncJobService;
    }

    /** 只读文件，不触发刷新。 */
    public MarketCrashRiskVO load() {
        return toVo(readFile());
    }

    /** 本周已成功则返回缓存，否则提交异步刷新并立刻返回（可带上周正文）。 */
    public MarketCrashRiskVO ensureCurrentWeek() {
        return ensureCurrentWeekOn(LocalDate.now());
    }

    /** 可注入日期以便单测同周/跨周。 */
    MarketCrashRiskVO ensureCurrentWeekOn(LocalDate today) {
        String weekKey = weekKey(today);
        CrashRiskFile file = readFile();
        if (weekKey.equals(file.weekKey) && STATUS_SUCCEEDED.equals(file.status)) {
            return toVo(file);
        }
        if (weekKey.equals(file.weekKey) && STATUS_RUNNING.equals(file.status)) {
            return toVo(file);
        }
        file.weekKey = weekKey;
        file.status = STATUS_RUNNING;
        file.errorMessage = "";
        file.generatedAt = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
        writeFile(file);
        AsyncJobVO job = asyncJobService.submit(
                JOB_TYPE, "美股大回撤情景预警：" + weekKey, weekKey, null, false);
        file.jobId = job.getId();
        writeFile(file);
        return toVo(file);
    }

    /** JobHandler 调用：联网检索并写回三字段。 */
    public void runRefresh(String weekKey) {
        if (!StringUtils.hasText(weekKey)) {
            throw new IllegalArgumentException("market_crash_risk 缺少 refId");
        }
        CrashRiskFile file = readFile();
        if (StringUtils.hasText(file.weekKey) && !weekKey.equals(file.weekKey)) {
            log.info("跳过过期预警任务 expected={} actual={}", weekKey, file.weekKey);
            return;
        }
        try {
            String text = searchWorker.run(buildUserPrompt(), null);
            CrashFields fields = parseFields(text);
            file.weekKey = weekKey;
            file.window = fields.window;
            file.trigger = fields.trigger;
            file.impact = fields.impact;
            file.status = STATUS_SUCCEEDED;
            file.errorMessage = "";
            file.generatedAt = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
            writeFile(file);
        } catch (RuntimeException e) {
            file.weekKey = weekKey;
            file.status = STATUS_FAILED;
            file.errorMessage = e.getMessage() == null ? "预警生成失败" : e.getMessage();
            file.generatedAt = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
            writeFile(file);
            throw e;
        }
    }

    /** ISO 年-周，如 2026-W37。 */
    static String weekKey(LocalDate date) {
        int year = date.get(ISO_WEEK.weekBasedYear());
        int week = date.get(ISO_WEEK.weekOfWeekBasedYear());
        return year + "-W" + String.format(Locale.ROOT, "%02d", week);
    }

    /** 从模型文本抽出 window / trigger / impact。 */
    CrashFields parseFields(String text) {
        if (!StringUtils.hasText(text)) {
            throw new IllegalArgumentException("模型未返回有效预警");
        }
        String json = text.trim();
        int start = json.indexOf('{');
        int end = json.lastIndexOf('}');
        if (start >= 0 && end > start) {
            json = json.substring(start, end + 1);
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            String window = node.path("window").asText("").trim();
            String trigger = node.path("trigger").asText("").trim();
            String impact = node.path("impact").asText("").trim();
            if (!StringUtils.hasText(window) || !StringUtils.hasText(trigger) || !StringUtils.hasText(impact)) {
                throw new IllegalArgumentException("模型未返回完整预警字段");
            }
            return new CrashFields(window, trigger, impact);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("无法解析预警结果");
        }
    }

    private static String buildUserPrompt() {
        return """
                请先用 searchWeb 检索近期美股（标普 500 / 纳指）大回撤、衰退、利率、就业、地缘、估值与信贷风险的中英文资料。
                然后给出情景分析，回答：下一次美股市场大回撤可能在何时出现、可能由何种因素触发、可能出现何种影响。
                时间窗口用「数周到数月」量级，禁止精确到某一天的预言。明确这不是可兑现的预测，也不是投资建议。
                只输出 JSON：{"window":"...","trigger":"...","impact":"..."}
                """;
    }

    private CrashRiskFile readFile() {
        Path path = Path.of(properties.getCrashRiskFile());
        if (!Files.isRegularFile(path)) {
            return new CrashRiskFile();
        }
        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            if (!StringUtils.hasText(json)) {
                return new CrashRiskFile();
            }
            CrashRiskFile file = objectMapper.readValue(json, CrashRiskFile.class);
            return file == null ? new CrashRiskFile() : file;
        } catch (IOException e) {
            log.warn("读取回撤预警失败 path={}: {}", path, e.getMessage());
            return new CrashRiskFile();
        }
    }

    private void writeFile(CrashRiskFile file) {
        Path path = Path.of(properties.getCrashRiskFile());
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(file);
            Files.writeString(path, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("写入回撤预警失败 path={}: {}", path, e.getMessage());
            throw new IllegalArgumentException("无法保存回撤预警");
        }
    }

    private static MarketCrashRiskVO toVo(CrashRiskFile file) {
        MarketCrashRiskVO vo = new MarketCrashRiskVO();
        vo.setWeekKey(nullToEmpty(file.weekKey));
        vo.setGeneratedAt(nullToEmpty(file.generatedAt));
        vo.setWindow(nullToEmpty(file.window));
        vo.setTrigger(nullToEmpty(file.trigger));
        vo.setImpact(nullToEmpty(file.impact));
        vo.setStatus(nullToEmpty(file.status));
        vo.setJobId(nullToEmpty(file.jobId));
        vo.setErrorMessage(nullToEmpty(file.errorMessage));
        return vo;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /** 模型三字段。 */
    record CrashFields(String window, String trigger, String impact) {
    }

    /** JSON 落盘结构。 */
    static class CrashRiskFile {
        public String weekKey = "";
        public String generatedAt = "";
        public String window = "";
        public String trigger = "";
        public String impact = "";
        public String status = "";
        public String jobId = "";
        public String errorMessage = "";
    }
}
