package com.mychat.utils;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 多厂商 reasoning / thinking 字段统一提取。
 * <p>
 * 优先级：{@code reasoningContent} → {@code thinking} → {@code reasoning}
 * → 反射 {@code getReasoningContent()}（无则跳过）。
 */
public final class ReasoningContentExtractor {

    private ReasoningContentExtractor() {
    }

    public static String extract(ChatResponse response) {
        if (response == null || response.getResult() == null) {
            return null;
        }
        Generation generation = response.getResult();
        String fromMeta = extractFromMetadata(generation.getMetadata());
        if (fromMeta != null) {
            return fromMeta;
        }
        return extractViaReflection(generation.getOutput());
    }

    public static String extractFromMetadata(ChatGenerationMetadata metadata) {
        if (metadata == null) {
            return null;
        }
        String v = asNonBlankString(metadata.get("reasoningContent"));
        if (v != null) {
            return v;
        }
        v = asNonBlankString(metadata.get("thinking"));
        if (v != null) {
            return v;
        }
        return asNonBlankString(metadata.get("reasoning"));
    }

    /**
     * 累计全文 → 只发后缀；纯 delta / 新段落 → 原样发；相同 → 不发。
     */
    public static String nextDelta(AtomicReference<String> lastSeen, String candidate) {
        if (!StringUtils.hasText(candidate)) {
            return null;
        }
        String current = candidate;
        String prev = lastSeen != null ? lastSeen.get() : null;
        if (prev == null || prev.isEmpty()) {
            if (lastSeen != null) {
                lastSeen.set(current);
            }
            return current;
        }
        if (current.equals(prev)) {
            return null;
        }
        if (current.startsWith(prev)) {
            String suffix = current.substring(prev.length());
            if (lastSeen != null) {
                lastSeen.set(current);
            }
            return StringUtils.hasText(suffix) ? suffix : null;
        }
        if (lastSeen != null) {
            lastSeen.set(current);
        }
        return current;
    }

    private static String extractViaReflection(AssistantMessage output) {
        if (output == null) {
            return null;
        }
        try {
            Method m = output.getClass().getMethod("getReasoningContent");
            return asNonBlankString(m.invoke(output));
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static String asNonBlankString(Object value) {
        if (value == null) {
            return null;
        }
        String s = value instanceof String str ? str : String.valueOf(value);
        return StringUtils.hasText(s) ? s : null;
    }
}
