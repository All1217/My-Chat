package com.mychat.service.knowledge;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 入库切段后的 Transformer：为每块生成短摘要并拼进 {@link Document} 文本，再交给 embedding。
 */
@Slf4j
@Service
public class ChunkSummaryService {

    /** 送给摘要模型的原文上限，避免单块撑爆上下文 */
    public static final int SUMMARIZE_INPUT_MAX_CHARS = 4000;

    static final String META_SUMMARY = "summary";
    static final String META_ORIGINAL = "original";

    private static final String SYSTEM_PROMPT = """
            你是知识库切片摘要器。只根据用户给出的原文写 2～4 句中文摘要。
            要求：概括本段主题，并点出它能回答哪类问题。
            禁止发挥、禁止标题、禁止列表、禁止复述成超长段落。
            只输出摘要正文。
            """;

    private final ChatClient chatClient;

    public ChunkSummaryService(@Qualifier("agentWorkflowChatClient") ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * 顺序为每块生成摘要并拼进 content；单块失败则保留原文。
     */
    public List<Document> enrich(List<Document> segments) {
        if (segments == null || segments.isEmpty()) {
            return segments == null ? List.of() : segments;
        }
        List<Document> out = new ArrayList<>(segments.size());
        int n = segments.size();
        for (int i = 0; i < n; i++) {
            out.add(enrichOne(segments.get(i), i + 1, n));
        }
        return out;
    }

    /**
     * 把摘要与原文拼成入库/检索用的 content。
     */
    public static String mergeContent(String summary, String original) {
        String body = original == null ? "" : original;
        if (!StringUtils.hasText(summary)) {
            return body;
        }
        return "【摘要】\n" + summary.trim() + "\n\n【原文】\n" + body;
    }

    /**
     * 单块：调模型写摘要；失败则原样返回。
     */
    Document enrichOne(Document segment, int index, int total) {
        if (segment == null) {
            return segment;
        }
        String original = segment.getText() != null ? segment.getText() : "";
        log.info("生成 chunk 摘要 {}/{} chars={}", index, total, original.length());
        try {
            String summary = summarizeOne(original);
            if (!StringUtils.hasText(summary)) {
                return copyWithOriginal(segment, original);
            }
            Map<String, Object> meta = copyMeta(segment);
            meta.put(META_SUMMARY, summary.trim());
            meta.put(META_ORIGINAL, original);
            return new Document(segment.getId(), mergeContent(summary, original), meta);
        } catch (Exception e) {
            log.warn("chunk 摘要失败 {}/{}，降级为仅原文: {}", index, total, e.getMessage());
            return copyWithOriginal(segment, original);
        }
    }

    /**
     * 调用无工具 ChatClient 写摘要；空原文直接跳过。
     */
    String summarizeOne(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String input = text.trim();
        if (input.length() > SUMMARIZE_INPUT_MAX_CHARS) {
            input = input.substring(0, SUMMARIZE_INPUT_MAX_CHARS);
        }
        String content = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(input)
                .call()
                .content();
        return content != null ? content.trim() : "";
    }

    private static Document copyWithOriginal(Document segment, String original) {
        Map<String, Object> meta = copyMeta(segment);
        meta.put(META_ORIGINAL, original);
        return new Document(segment.getId(), original, meta);
    }

    private static Map<String, Object> copyMeta(Document segment) {
        Map<String, Object> meta = new HashMap<>();
        if (segment.getMetadata() != null) {
            meta.putAll(segment.getMetadata());
        }
        return meta;
    }
}
