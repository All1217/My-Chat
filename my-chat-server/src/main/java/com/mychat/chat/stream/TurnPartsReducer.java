package com.mychat.chat.stream;

import com.mychat.common.ChatStreamEvent;
import com.mychat.vo.MessagePartVO;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将本轮 NDJSON {@link ChatStreamEvent} 归约为可落库的 parts / thinking / 正文。
 * <p>
 * 与前端 {@code useChatStream.applyStreamEvent} 语义对齐，但不把 text/thinking 再塞进 parts。
 */
public final class TurnPartsReducer {

    private TurnPartsReducer() {
    }

    /**
     * @param markRunningAsCancelled 流被取消或出错时，将仍为 running 的工具标为 cancelled
     */
    public static TurnSnapshot reduce(List<ChatStreamEvent> events, boolean markRunningAsCancelled) {
        Map<String, MessagePartVO> toolsById = new LinkedHashMap<>();
        StringBuilder text = new StringBuilder();
        StringBuilder thinking = new StringBuilder();

        if (events != null) {
            for (ChatStreamEvent e : events) {
                if (e == null || e.type() == null) {
                    continue;
                }
                switch (e.type()) {
                    case ChatStreamEvent.TYPE_TEXT_DELTA -> {
                        if (e.text() != null) {
                            text.append(e.text());
                        }
                    }
                    case ChatStreamEvent.TYPE_THINKING_DELTA -> {
                        if (e.text() != null) {
                            thinking.append(e.text());
                        }
                    }
                    case ChatStreamEvent.TYPE_TOOL_CALL -> applyToolCall(toolsById, e);
                    case ChatStreamEvent.TYPE_TOOL_RESULT -> applyToolResult(toolsById, e);
                    default -> {
                        // error / done：不进入 parts
                    }
                }
            }
        }

        if (markRunningAsCancelled) {
            for (MessagePartVO part : toolsById.values()) {
                if ("running".equals(part.getStatus())) {
                    part.setStatus("cancelled");
                }
            }
        }

        String thinkingStr = thinking.toString().trim();
        return new TurnSnapshot(
                new ArrayList<>(toolsById.values()),
                thinkingStr.isEmpty() ? null : thinkingStr,
                text.toString()
        );
    }

    private static void applyToolCall(Map<String, MessagePartVO> toolsById, ChatStreamEvent e) {
        if (e.id() == null || e.id().isBlank()) {
            return;
        }
        MessagePartVO part = toolsById.computeIfAbsent(e.id(), id -> {
            MessagePartVO p = new MessagePartVO();
            p.setType("tool");
            p.setId(id);
            p.setStatus("running");
            return p;
        });
        if (e.name() != null) {
            part.setName(e.name());
        }
        if (e.args() != null) {
            part.setArgs(e.args());
        }
        if (!"done".equals(part.getStatus()) && !"error".equals(part.getStatus())) {
            part.setStatus("running");
        }
    }

    private static void applyToolResult(Map<String, MessagePartVO> toolsById, ChatStreamEvent e) {
        if (e.id() == null || e.id().isBlank()) {
            return;
        }
        MessagePartVO part = toolsById.computeIfAbsent(e.id(), id -> {
            MessagePartVO p = new MessagePartVO();
            p.setType("tool");
            p.setId(id);
            return p;
        });
        if (e.name() != null) {
            part.setName(e.name());
        }
        boolean ok = Boolean.TRUE.equals(e.ok());
        part.setOk(ok);
        part.setStatus(ok ? "done" : "error");
        part.setResultPreview(unwrapToolPreview(e.preview()));
        if (e.truncated() != null) {
            part.setTruncated(e.truncated());
        }
    }

    /**
     * 与前端 unwrapToolPreview 一致：剥掉一层多余的 JSON 字符串包装。
     */
    public static String unwrapToolPreview(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        String s = raw.trim();
        if (s.length() >= 2 && s.charAt(0) == '"' && s.charAt(s.length() - 1) == '"') {
            try {
                // 简单去一层 JSON 字符串：\"...\" → ...
                com.fasterxml.jackson.databind.ObjectMapper mapper =
                        new com.fasterxml.jackson.databind.ObjectMapper();
                Object once = mapper.readValue(s, Object.class);
                if (once instanceof String str) {
                    return str;
                }
            } catch (Exception ignored) {
                // 非合法 JSON 则原样返回
            }
        }
        return raw;
    }

    public record TurnSnapshot(List<MessagePartVO> parts, String thinking, String assistantText) {
        public boolean hasTrajectory() {
            return (parts != null && !parts.isEmpty())
                    || (thinking != null && !thinking.isBlank());
        }
    }
}
