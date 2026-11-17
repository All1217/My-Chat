package com.mychat.utils;

import com.mychat.common.ChatStreamEvent;
import com.mychat.vo.MessagePartVO;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 将本轮 NDJSON {@link ChatStreamEvent} 归约为可落库的 parts / thinking / 正文。
 * <p>
 * 与前端 {@code useChatStream.applyStreamEvent} 语义对齐，但不把 text/thinking 再塞进 parts。
 * 支持 {@code route} / {@code step} 片段（与工具一并进入时间线）。
 */
public final class TurnPartsReducer {

    private TurnPartsReducer() {
    }

    /**
     * @param markRunningAsCancelled 流被取消或出错时，将仍为 running 的工具标为 cancelled
     */
    public static TurnSnapshot reduce(List<ChatStreamEvent> events, boolean markRunningAsCancelled) {
        List<MessagePartVO> ordered = new ArrayList<>();
        Map<String, MessagePartVO> toolsById = new LinkedHashMap<>();
        Map<String, MessagePartVO> stepsById = new LinkedHashMap<>();
        StringBuilder text = new StringBuilder();

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
                    case ChatStreamEvent.TYPE_THINKING_DELTA -> { /* Phase 3 不持久化模型思考 */ }
                    case ChatStreamEvent.TYPE_ROUTE -> applyRoute(ordered, e);
                    case ChatStreamEvent.TYPE_STEP -> applyStep(ordered, stepsById, e);
                    case ChatStreamEvent.TYPE_TOOL_CALL -> applyToolCall(ordered, toolsById, e);
                    case ChatStreamEvent.TYPE_TOOL_RESULT -> applyToolResult(ordered, toolsById, e);
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
            for (MessagePartVO part : stepsById.values()) {
                if ("running".equals(part.getStatus())) {
                    part.setStatus("cancelled");
                }
            }
        }

        return new TurnSnapshot(
                ordered,
                null,
                text.toString()
        );
    }

    private static void applyRoute(List<MessagePartVO> ordered, ChatStreamEvent e) {
        MessagePartVO part = new MessagePartVO();
        part.setType("route");
        part.setId(e.stepId() != null ? e.stepId() : "route-" + UUID.randomUUID());
        part.setName(e.name() != null ? e.name() : "general");
        part.setStatus("done");
        part.setResultPreview(e.text());
        ordered.add(part);
    }

    /** Orchestrator / 质量环步骤 → parts.type=step */
    private static void applyStep(List<MessagePartVO> ordered,
                                  Map<String, MessagePartVO> stepsById,
                                  ChatStreamEvent e) {
        String id = e.stepId() != null ? e.stepId()
                : e.id() != null ? e.id() : "step-" + UUID.randomUUID();
        boolean isNew = !stepsById.containsKey(id);
        MessagePartVO part = stepsById.computeIfAbsent(id, ignored -> new MessagePartVO());
        part.setType("step");
        part.setId(id);
        part.setName(e.name() != null ? e.name() : "unknown");
        part.setStatus(publicStatus(e.status(), "done"));
        part.setArgs(e.args());
        part.setParentStepId(e.parentStepId());
        part.setDisplayName(e.displayName());
        part.setElapsedMs(e.elapsedMs());
        StringBuilder preview = new StringBuilder();
        if (e.preview() != null && !e.preview().isBlank()) {
            preview.append(e.preview());
        }
        if (!preview.isEmpty()) {
            part.setResultPreview(preview.toString());
        }
        if (e.truncated() != null) {
            part.setTruncated(e.truncated());
        }
        if (isNew) {
            ordered.add(part);
        }
    }

    private static void applyToolCall(
            List<MessagePartVO> ordered,
            Map<String, MessagePartVO> toolsById,
            ChatStreamEvent e) {
        String id = e.stepId() != null ? e.stepId() : e.id();
        if (id == null || id.isBlank()) {
            return;
        }
        boolean isNew = !toolsById.containsKey(id);
        MessagePartVO part = toolsById.computeIfAbsent(id, ignored -> {
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
        part.setParentStepId(e.parentStepId());
        part.setDisplayName(e.displayName());
        part.setElapsedMs(e.elapsedMs());
        if (!"done".equals(part.getStatus()) && !"error".equals(part.getStatus())) {
            part.setStatus("running");
        }
        if (isNew) {
            ordered.add(part);
        }
    }

    private static void applyToolResult(
            List<MessagePartVO> ordered,
            Map<String, MessagePartVO> toolsById,
            ChatStreamEvent e) {
        String id = e.stepId() != null ? e.stepId() : e.id();
        if (id == null || id.isBlank()) {
            return;
        }
        boolean isNew = !toolsById.containsKey(id);
        MessagePartVO part = toolsById.computeIfAbsent(id, ignored -> {
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
        part.setStatus(publicStatus(e.status(), ok ? "done" : "error"));
        part.setResultPreview(unwrapToolPreview(e.preview()));
        part.setParentStepId(e.parentStepId());
        part.setDisplayName(e.displayName());
        part.setElapsedMs(e.elapsedMs());
        if (e.truncated() != null) {
            part.setTruncated(e.truncated());
        }
        if (isNew) {
            ordered.add(part);
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

    /** 将扩展状态映射为旧前端可识别状态。 */
    private static String publicStatus(String status, String fallback) {
        if (status == null || status.isBlank()) {
            return fallback;
        }
        return switch (status) {
            case "PENDING", "RUNNING" -> "running";
            case "SUCCEEDED", "SKIPPED" -> "done";
            case "FAILED" -> "error";
            case "CANCELLED" -> "cancelled";
            default -> fallback;
        };
    }

    public record TurnSnapshot(List<MessagePartVO> parts, String thinking, String assistantText) {
        public boolean hasTrajectory() {
            return (parts != null && !parts.isEmpty())
                    || (thinking != null && !thinking.isBlank());
        }
    }
}
