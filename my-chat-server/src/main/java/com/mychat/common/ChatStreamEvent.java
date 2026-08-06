package com.mychat.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 主聊天 NDJSON 流式事件（进阶 3 · 第 1 周协议）。
 * <p>
 * 每行一个 JSON；{@code v=1}。plain 路径不使用本类型。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatStreamEvent(
        int v,
        String type,
        String turnId,
        int seq,
        String text,
        String id,
        String name,
        Object args,
        Boolean ok,
        String preview,
        Boolean truncated,
        String message
) {
    public static final int VERSION = 1;
    public static final int PREVIEW_MAX_CHARS = 4096;

    public static final String TYPE_THINKING_DELTA = "thinking_delta";
    public static final String TYPE_TEXT_DELTA = "text_delta";
    public static final String TYPE_TOOL_CALL = "tool_call";
    public static final String TYPE_TOOL_RESULT = "tool_result";
    /** Routing Workflow 分类结果：name=路由标签，text=分类理由 */
    public static final String TYPE_ROUTE = "route";
    public static final String TYPE_ERROR = "error";
    public static final String TYPE_DONE = "done";

    public static ChatStreamEvent thinkingDelta(String turnId, AtomicInteger seq, String text) {
        return new ChatStreamEvent(VERSION, TYPE_THINKING_DELTA, turnId, seq.incrementAndGet(),
                text, null, null, null, null, null, null, null);
    }

    public static ChatStreamEvent textDelta(String turnId, AtomicInteger seq, String text) {
        return new ChatStreamEvent(VERSION, TYPE_TEXT_DELTA, turnId, seq.incrementAndGet(),
                text, null, null, null, null, null, null, null);
    }

    /**
     * 路由决策事件（主聊天 NDJSON 首步）。
     *
     * @param route     file / kb / search / general
     * @param reasoning 分类器简要理由
     */
    public static ChatStreamEvent route(String turnId, AtomicInteger seq, String route, String reasoning) {
        return new ChatStreamEvent(VERSION, TYPE_ROUTE, turnId, seq.incrementAndGet(),
                reasoning, null, route, null, null, null, null, null);
    }

    public static ChatStreamEvent toolCall(String turnId, AtomicInteger seq,
                                          String id, String name, Object args) {
        return new ChatStreamEvent(VERSION, TYPE_TOOL_CALL, turnId, seq.incrementAndGet(),
                null, id, name, args, null, null, null, null);
    }

    public static ChatStreamEvent toolResult(String turnId, AtomicInteger seq,
                                            String id, String name, boolean ok,
                                            String preview, boolean truncated) {
        return new ChatStreamEvent(VERSION, TYPE_TOOL_RESULT, turnId, seq.incrementAndGet(),
                null, id, name, null, ok, preview, truncated, null);
    }

    public static ChatStreamEvent error(String turnId, AtomicInteger seq, String message) {
        return new ChatStreamEvent(VERSION, TYPE_ERROR, turnId, seq.incrementAndGet(),
                null, null, null, null, null, null, null, message);
    }

    public static ChatStreamEvent done(String turnId, AtomicInteger seq) {
        return new ChatStreamEvent(VERSION, TYPE_DONE, turnId, seq.incrementAndGet(),
                null, null, null, null, null, null, null, null);
    }

    /**
     * 截断工具结果预览，避免大 HTML / 长文件内容撑爆客户端。
     *
     * @return [0]=preview 文本，[1]=是否截断（"true"/"false"）
     */
    public static String[] truncatePreview(String raw) {
        if (raw == null) {
            return new String[]{"", "false"};
        }
        if (raw.length() <= PREVIEW_MAX_CHARS) {
            return new String[]{raw, "false"};
        }
        return new String[]{raw.substring(0, PREVIEW_MAX_CHARS), "true"};
    }

    /** args 解析失败时的兜底结构 */
    public static Map<String, String> rawArgs(String raw) {
        return Map.of("raw", raw != null ? raw : "");
    }
}
