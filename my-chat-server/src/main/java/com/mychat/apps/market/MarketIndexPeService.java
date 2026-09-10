package com.mychat.apps.market;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mychat.apps.market.entity.vo.MarketIndexPeItemVO;
import com.mychat.apps.market.entity.vo.MarketIndexPeVO;
import com.mychat.service.agent.worker.SearchWorker;
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
import java.util.ArrayList;
import java.util.List;

/** 三大美股指数市盈率与近五年分位：按自然日缓存，跨日联网检索。 */
@Slf4j
@Service
public class MarketIndexPeService {

    static final String STATUS_SUCCEEDED = "SUCCEEDED";
    static final String STATUS_FAILED = "FAILED";
    static final String SHORT_SAMPLE_COMMENT = "资料不足";

    private static final IndexSpec[] SPECS = {
            new IndexSpec("SPX", "标普500"),
            new IndexSpec("DJIA", "道琼斯"),
            new IndexSpec("NDX", "纳斯达克100")
    };

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MarketStrategyProperties properties;
    private final SearchWorker searchWorker;

    /** 注入落盘路径与联网搜索 Worker。 */
    public MarketIndexPeService(MarketStrategyProperties properties, SearchWorker searchWorker) {
        this.properties = properties;
        this.searchWorker = searchWorker;
    }

    /** 当天已成功且至少有一条 PE/分位则读文件，否则同步刷新。 */
    public MarketIndexPeVO ensureToday() {
        return ensureTodayOn(LocalDate.now());
    }

    /** 可注入日期以便单测同日/跨日。 */
    MarketIndexPeVO ensureTodayOn(LocalDate today) {
        String dateKey = today.toString();
        IndexPeFile file = readFile();
        if (dateKey.equals(file.cacheDate)
                && STATUS_SUCCEEDED.equals(file.status)
                && countPe(file) > 0) {
            return toVo(file);
        }
        try {
            IndexPeFile refreshed = refresh(today, file);
            writeFile(refreshed);
            return toVo(refreshed);
        } catch (RuntimeException e) {
            log.warn("指数市盈率刷新失败: {}", e.getMessage());
            file.cacheDate = dateKey;
            file.status = STATUS_FAILED;
            file.errorMessage = e.getMessage() == null ? "指数市盈率刷新失败" : e.getMessage();
            file.generatedAt = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
            writeFile(file);
            return toVo(file);
        }
    }

    /** 一次 searchWeb，解析三指数后覆盖缓存。 */
    private IndexPeFile refresh(LocalDate today, IndexPeFile previous) {
        String text = searchWorker.run(buildUserPrompt(), null);
        List<MarketIndexPeItemVO> items = parseItems(text);
        boolean anyData = items.stream().anyMatch(i -> i.getPe() != null || i.getPercentile() != null);
        if (!anyData) {
            throw new IllegalArgumentException("模型未返回有效指数估值");
        }
        IndexPeFile file = previous == null ? new IndexPeFile() : previous;
        file.cacheDate = today.toString();
        file.status = STATUS_SUCCEEDED;
        file.errorMessage = "";
        file.generatedAt = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
        file.indices = items;
        return file;
    }

    /** 从模型文本抽出与指数顺序对齐的三条。 */
    List<MarketIndexPeItemVO> parseItems(String text) {
        List<MarketIndexPeItemVO> ordered = emptyRows();
        if (!StringUtils.hasText(text)) {
            return ordered;
        }
        String json = text.trim();
        int start = json.indexOf('[');
        int end = json.lastIndexOf(']');
        if (start >= 0 && end > start) {
            json = json.substring(start, end + 1);
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (!node.isArray()) {
                return ordered;
            }
            for (JsonNode row : node) {
                String code = row.path("code").asText("").trim().toUpperCase();
                int idx = indexOf(code);
                if (idx < 0) {
                    continue;
                }
                MarketIndexPeItemVO item = ordered.get(idx);
                item.setName(StringUtils.hasText(row.path("name").asText(""))
                        ? row.path("name").asText().trim()
                        : SPECS[idx].name);
                item.setPe(parsePositive(row.get("pe")));
                item.setPercentile(parsePercentile(row.get("percentile")));
                String comment = row.path("comment").asText("").trim();
                if (!StringUtils.hasText(comment)
                        && item.getPe() == null && item.getPercentile() == null) {
                    comment = SHORT_SAMPLE_COMMENT;
                }
                item.setComment(comment);
                item.setSampleSize(0);
            }
        } catch (Exception e) {
            log.warn("解析指数估值失败: {}", e.getMessage());
        }
        for (MarketIndexPeItemVO item : ordered) {
            if (!StringUtils.hasText(item.getComment())
                    && item.getPe() == null && item.getPercentile() == null) {
                item.setComment(SHORT_SAMPLE_COMMENT);
            }
        }
        return ordered;
    }

    private static List<MarketIndexPeItemVO> emptyRows() {
        List<MarketIndexPeItemVO> rows = new ArrayList<>();
        for (IndexSpec spec : SPECS) {
            MarketIndexPeItemVO item = new MarketIndexPeItemVO();
            item.setCode(spec.code);
            item.setName(spec.name);
            item.setComment("");
            rows.add(item);
        }
        return rows;
    }

    private static int indexOf(String code) {
        for (int i = 0; i < SPECS.length; i++) {
            if (SPECS[i].code.equalsIgnoreCase(code)) {
                return i;
            }
        }
        return -1;
    }

    static Double parsePositive(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        double value;
        if (node.isNumber()) {
            value = node.asDouble();
        } else {
            String text = node.asText("").trim().replace("%", "");
            if (!StringUtils.hasText(text) || "-".equals(text) || "—".equals(text)) {
                return null;
            }
            try {
                value = Double.parseDouble(text);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        if (Double.isNaN(value) || Double.isInfinite(value) || value <= 0 || value > 10_000) {
            return null;
        }
        return value;
    }

    static Double parsePercentile(JsonNode node) {
        Double value = parsePositive(node);
        if (value == null) {
            return null;
        }
        if (value > 100) {
            return 100.0;
        }
        return value;
    }

    private static String buildUserPrompt() {
        return """
                请先用 searchWeb 检索标普500、道琼斯工业平均指数、纳斯达克100 的当前市盈率（优先 TTM）以及近五年分位。
                数字允许粗略，来自近期公开报道即可。禁止保证收益，禁止精确点位预言，明确这不是投资建议。
                只输出与下列顺序对齐的 JSON 数组：
                [{"code":"SPX","name":"标普500","pe":28.1,"percentile":82,"comment":"一句中文短评"},
                 {"code":"DJIA","name":"道琼斯","pe":22.0,"percentile":60,"comment":"..."},
                 {"code":"NDX","name":"纳斯达克100","pe":35.0,"percentile":90,"comment":"..."}]
                缺资料的 pe 或 percentile 用 null，comment 写「资料不足」。
                """;
    }

    private IndexPeFile readFile() {
        Path path = Path.of(properties.getIndexPeFile());
        if (!Files.isRegularFile(path)) {
            return new IndexPeFile();
        }
        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            if (!StringUtils.hasText(json)) {
                return new IndexPeFile();
            }
            IndexPeFile file = objectMapper.readValue(json, IndexPeFile.class);
            return file == null ? new IndexPeFile() : file;
        } catch (IOException e) {
            log.warn("读取指数市盈率文件失败 path={}: {}", path, e.getMessage());
            return new IndexPeFile();
        }
    }

    private void writeFile(IndexPeFile file) {
        Path path = Path.of(properties.getIndexPeFile());
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(file);
            Files.writeString(path, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("写入指数市盈率文件失败 path={}: {}", path, e.getMessage());
            throw new IllegalArgumentException("无法保存指数市盈率");
        }
    }

    private static MarketIndexPeVO toVo(IndexPeFile file) {
        MarketIndexPeVO vo = new MarketIndexPeVO();
        vo.setCacheDate(file.cacheDate == null ? "" : file.cacheDate);
        vo.setGeneratedAt(file.generatedAt == null ? "" : file.generatedAt);
        vo.setStatus(file.status == null ? "" : file.status);
        vo.setErrorMessage(file.errorMessage == null ? "" : file.errorMessage);
        List<MarketIndexPeItemVO> indices = new ArrayList<>();
        if (file.indices != null) {
            for (MarketIndexPeItemVO item : file.indices) {
                indices.add(copyItem(item));
            }
        }
        vo.setIndices(indices);
        return vo;
    }

    private static MarketIndexPeItemVO copyItem(MarketIndexPeItemVO item) {
        MarketIndexPeItemVO copy = new MarketIndexPeItemVO();
        if (item == null) {
            return copy;
        }
        copy.setCode(item.getCode());
        copy.setName(item.getName());
        copy.setPe(item.getPe());
        copy.setPercentile(item.getPercentile());
        copy.setSampleSize(item.getSampleSize());
        copy.setComment(item.getComment() == null ? "" : item.getComment());
        return copy;
    }

    /** 统计有 PE 或分位的条数。 */
    private static int countPe(IndexPeFile file) {
        if (file == null || file.indices == null) {
            return 0;
        }
        int n = 0;
        for (MarketIndexPeItemVO item : file.indices) {
            if (item != null && (item.getPe() != null || item.getPercentile() != null)) {
                n++;
            }
        }
        return n;
    }

    private record IndexSpec(String code, String name) {
    }

    /** JSON 落盘结构。 */
    static class IndexPeFile {
        public String cacheDate = "";
        public String generatedAt = "";
        public String status = "";
        public String errorMessage = "";
        public List<MarketIndexPeItemVO> indices = new ArrayList<>();
    }
}
