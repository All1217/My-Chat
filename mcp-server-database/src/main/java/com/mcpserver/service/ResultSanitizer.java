package com.mcpserver.service;

import com.mcpserver.config.DatabaseMcpProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 查询结果后处理：敏感列脱敏 + 超长单元格截断。
 */
@Component
public class ResultSanitizer {

    private final DatabaseMcpProperties properties;

    public ResultSanitizer(DatabaseMcpProperties properties) {
        this.properties = properties;
    }

    /**
     * 就地处理行列表，返回统计元数据。
     */
    public Map<String, Object> sanitize(List<Map<String, Object>> rows) {
        int truncatedCells = 0;
        int maskedCells = 0;
        int maxLen = Math.max(1, properties.getMaxCellLength());
        List<String> patterns = properties.getSensitiveColumnPatterns();
        if (patterns == null) {
            patterns = List.of();
        }

        for (Map<String, Object> row : rows) {
            for (Map.Entry<String, Object> entry : new ArrayList<>(row.entrySet())) {
                String col = entry.getKey();
                Object val = entry.getValue();
                if (val == null) {
                    continue;
                }
                if (isSensitive(col, patterns)) {
                    row.put(col, maskValue(val));
                    maskedCells++;
                    continue;
                }
                if (val instanceof String s && s.length() > maxLen) {
                    row.put(col, s.substring(0, maxLen) + "…");
                    truncatedCells++;
                }
            }
        }

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("sensitiveMasked", maskedCells > 0);
        meta.put("maskedCells", maskedCells);
        meta.put("truncated", truncatedCells > 0);
        meta.put("truncatedCells", truncatedCells);
        return meta;
    }

    /** 列名是否命中敏感模式。 */
    static boolean isSensitive(String columnName, List<String> patterns) {
        if (!StringUtils.hasText(columnName) || patterns == null || patterns.isEmpty()) {
            return false;
        }
        String lower = columnName.toLowerCase(Locale.ROOT);
        for (String p : patterns) {
            if (StringUtils.hasText(p) && lower.contains(p.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /** 短值全星；较长则保留头 3 尾 4。 */
    static String maskValue(Object raw) {
        String s = String.valueOf(raw);
        if (s.length() <= 8) {
            return "****";
        }
        return s.substring(0, 3) + "****" + s.substring(s.length() - 4);
    }
}
