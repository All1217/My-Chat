package com.mychat.apps.papertrade.agent;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** 将字符串或对象数组宽松解析为字符串列表（兼容模型把 evidenceRefs 写成对象）。 */
public class FlexibleStringListDeserializer extends JsonDeserializer<List<String>> {

    /** 解析文本或 {title,url,...} 对象为字符串。 */
    @Override
    public List<String> deserialize(JsonParser parser, DeserializationContext context)
            throws IOException {
        JsonNode node = parser.getCodec().readTree(parser);
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            return List.of(asRef(node));
        }
        List<String> out = new ArrayList<>();
        for (JsonNode item : node) {
            out.add(asRef(item));
        }
        return out;
    }

    private static String asRef(JsonNode item) {
        if (item == null || item.isNull()) {
            return "";
        }
        if (item.isTextual()) {
            return item.asText();
        }
        if (item.isObject()) {
            if (item.hasNonNull("title")) {
                return item.get("title").asText();
            }
            if (item.hasNonNull("url")) {
                return item.get("url").asText();
            }
            if (item.hasNonNull("source")) {
                return item.get("source").asText();
            }
        }
        return item.asText();
    }
}
