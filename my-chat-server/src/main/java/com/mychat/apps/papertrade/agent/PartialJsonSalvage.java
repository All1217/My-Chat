package com.mychat.apps.papertrade.agent;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/** 从被截断的 JSON 中抢救已完整的卡片/决策对象。 */
public final class PartialJsonSalvage {

    private PartialJsonSalvage() {
    }

    /** 抢救 ResearchCardBatch；至少一张完整卡才返回非空。 */
    public static ResearchCardBatch salvageCards(String raw, ObjectMapper mapper) {
        List<JsonNode> objects = extractCompleteObjects(raw, "cards");
        if (objects.isEmpty()) {
            objects = extractCompleteObjects(raw, null);
        }
        ObjectMapper lenient = mapper.copy()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        List<ResearchCardBatch.ResearchCardDecision> cards = new ArrayList<>();
        for (JsonNode node : objects) {
            if (!node.has("symbol")) {
                continue;
            }
            try {
                cards.add(lenient.treeToValue(node, ResearchCardBatch.ResearchCardDecision.class));
            } catch (Exception ignored) {
                // 跳过未完成对象
            }
        }
        return cards.isEmpty() ? null : new ResearchCardBatch(cards);
    }

    /** 抢救 TradeDecisionBatch。 */
    public static TradeDecisionBatch salvageDecisions(String raw, ObjectMapper mapper) {
        List<JsonNode> objects = extractCompleteObjects(raw, "decisions");
        if (objects.isEmpty()) {
            objects = extractCompleteObjects(raw, null);
        }
        ObjectMapper lenient = mapper.copy()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        List<TradeDecisionBatch.TradeDecisionProposal> decisions = new ArrayList<>();
        for (JsonNode node : objects) {
            if (!node.has("action") && !node.has("symbol")) {
                continue;
            }
            try {
                decisions.add(lenient.treeToValue(node, TradeDecisionBatch.TradeDecisionProposal.class));
            } catch (Exception ignored) {
                // 跳过未完成对象
            }
        }
        return decisions.isEmpty() ? null : new TradeDecisionBatch(decisions);
    }

    /**
     * 在文本中定位数组字段（或裸数组），按花括号配对提取完整对象。
     */
    static List<JsonNode> extractCompleteObjects(String raw, String arrayField) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        String text = unwrapNestedJson(raw);
        int arrayStart = -1;
        if (arrayField != null) {
            String marker = "\"" + arrayField + "\"";
            int fieldAt = text.indexOf(marker);
            if (fieldAt >= 0) {
                arrayStart = text.indexOf('[', fieldAt);
            }
        }
        if (arrayStart < 0) {
            arrayStart = text.indexOf('[');
        }
        if (arrayStart < 0) {
            return List.of();
        }
        List<JsonNode> out = new ArrayList<>();
        ObjectMapper probe = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        int i = arrayStart + 1;
        while (i < text.length()) {
            while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
                i++;
            }
            if (i >= text.length() || text.charAt(i) == ']') {
                break;
            }
            if (text.charAt(i) != '{') {
                i++;
                continue;
            }
            int end = findMatchingBrace(text, i);
            if (end < 0) {
                break;
            }
            String slice = text.substring(i, end + 1);
            try {
                out.add(probe.readTree(slice));
            } catch (Exception ignored) {
                break;
            }
            i = end + 1;
            while (i < text.length() && (text.charAt(i) == ',' || Character.isWhitespace(text.charAt(i)))) {
                i++;
            }
        }
        return out;
    }

    /** 若外层是 DecisionPayload/finalOutput 嵌套，尽量取出内层。 */
    static String unwrapNestedJson(String raw) {
        String text = raw.trim();
        if (text.contains("\"finalOutput\"")) {
            int fo = text.indexOf("\"finalOutput\"");
            int colon = text.indexOf(':', fo);
            int quote = text.indexOf('"', colon + 1);
            if (quote >= 0) {
                StringBuilder inner = new StringBuilder();
                for (int i = quote + 1; i < text.length(); i++) {
                    char c = text.charAt(i);
                    if (c == '\\' && i + 1 < text.length()) {
                        char n = text.charAt(++i);
                        switch (n) {
                            case 'n' -> inner.append('\n');
                            case 'r' -> inner.append('\r');
                            case 't' -> inner.append('\t');
                            case '"' -> inner.append('"');
                            case '\\' -> inner.append('\\');
                            default -> inner.append(n);
                        }
                        continue;
                    }
                    if (c == '"') {
                        break;
                    }
                    inner.append(c);
                }
                if (inner.indexOf("{") >= 0 || inner.indexOf("[") >= 0) {
                    return inner.toString();
                }
            }
        }
        return text;
    }

    /** 从 start 的 '{' 找到配对的 '}'，字符串内忽略。 */
    static int findMatchingBrace(String text, int start) {
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }
}
