package com.mychat.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.mychat.agent.api.AgentStepResult;
import com.mychat.agent.api.PublicProgressEvent;
import com.mychat.entity.dto.KnowledgeRetrieveHit;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
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
        String message,
        String runId,
        String stepId,
        String parentStepId,
        String status,
        String displayName,
        String visibility,
        String startedAt,
        Long elapsedMs
) {
    public static final int VERSION = 1;
    public static final int PREVIEW_MAX_CHARS = 4096;

    public static final String TYPE_THINKING_DELTA = "thinking_delta";
    public static final String TYPE_TEXT_DELTA = "text_delta";
    public static final String TYPE_TOOL_CALL = "tool_call";
    public static final String TYPE_TOOL_RESULT = "tool_result";
    /** Routing Workflow 分类结果：只公开路由标签，不公开分类 reasoning。 */
    public static final String TYPE_ROUTE = "route";
    /**
     * Orchestrator / 质量环步骤。
     * <ul>
     *   <li>{@code id} — step-{index}</li>
     *   <li>{@code name} — action（retrieve_kb / file / search / general / finish / evaluate_optimize）</li>
     *   <li>{@code text} — 保持为空，不公开 reasoning</li>
     *   <li>{@code args} — 含 instruction、stepIndex；retrieve_kb 可含 citations、kbScope</li>
     *   <li>{@code preview} — observation 截断预览</li>
     * </ul>
     */
    public static final String TYPE_STEP = "step";
    public static final String TYPE_ERROR = "error";
    public static final String TYPE_DONE = "done";

    public static ChatStreamEvent thinkingDelta(String turnId, AtomicInteger seq, String text) {
        return new ChatStreamEvent(VERSION, TYPE_THINKING_DELTA, turnId, seq.incrementAndGet(),
                text, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null);
    }

    public static ChatStreamEvent textDelta(String turnId, AtomicInteger seq, String text) {
        return new ChatStreamEvent(VERSION, TYPE_TEXT_DELTA, turnId, seq.incrementAndGet(),
                text, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null);
    }

    /**
     * 路由决策事件（主聊天 NDJSON 首步）。
     *
     * @param route     file / kb / search / general
     * @param reasoning 分类器简要理由
     */
    public static ChatStreamEvent route(String turnId, AtomicInteger seq, String route, String reasoning) {
        return new ChatStreamEvent(VERSION, TYPE_ROUTE, turnId, seq.incrementAndGet(),
                null, null, route, null, null, null, null, null,
                null, null, null, "SUCCEEDED", route, "public", null, null);
    }

    /**
     * 编排 / 质量环单步事件（无引用；质量环等走此重载）。
     */
    public static ChatStreamEvent step(
            String turnId,
            AtomicInteger seq,
            int stepIndex,
            String action,
            String reasoning,
            String instruction,
            String observationPreview) {
        return step(turnId, seq, stepIndex, action, reasoning, instruction, observationPreview, null, null);
    }

    /**
     * 编排单步事件；retrieve_kb 可带 citations（协议仍 v=1，只扩 args）。
     */
    public static ChatStreamEvent step(
            String turnId,
            AtomicInteger seq,
            int stepIndex,
            String action,
            String reasoning,
            String instruction,
            String observationPreview,
            List<KnowledgeRetrieveHit> citations) {
        return step(turnId, seq, stepIndex, action, reasoning, instruction, observationPreview, citations, null);
    }

    /**
     * 编排单步事件；retrieve_kb 可带 {@code args.citations} 与 {@code args.kbScope}。
     *
     * @param stepIndex          从 1 开始
     * @param action             next_action 或 evaluate_optimize
     * @param reasoning          简要理由
     * @param instruction        子任务指令（可空）
     * @param observationPreview Worker 观察预览（可空）
     * @param citations          知识库来源列表（可空；非空才写入 args）
     * @param kbScope            catalog|vector（可空；非空才写入 args）
     */
    public static ChatStreamEvent step(
            String turnId,
            AtomicInteger seq,
            int stepIndex,
            String action,
            String reasoning,
            String instruction,
            String observationPreview,
            List<KnowledgeRetrieveHit> citations,
            String kbScope) {
        String[] trunc = truncatePreview(observationPreview);
        Map<String, Object> args = new HashMap<>();
        args.put("stepIndex", stepIndex);
        args.put("instruction", instruction != null ? instruction : "");
        if (citations != null && !citations.isEmpty()) {
            args.put("citations", citations);
        }
        if (kbScope != null && !kbScope.isBlank()) {
            args.put("kbScope", kbScope.trim());
        }
        return new ChatStreamEvent(
                VERSION,
                TYPE_STEP,
                turnId,
                seq.incrementAndGet(),
                null,
                "step-" + stepIndex,
                action,
                args,
                null,
                trunc[0],
                Boolean.parseBoolean(trunc[1]),
                null,
                null,
                "step-" + stepIndex,
                null,
                "SUCCEEDED",
                action,
                "public",
                null,
                null);
    }

    public static ChatStreamEvent toolCall(String turnId, AtomicInteger seq,
                                          String id, String name, Object args) {
        return new ChatStreamEvent(VERSION, TYPE_TOOL_CALL, turnId, seq.incrementAndGet(),
                null, id, name, safeArgs(args), null, null, null, null,
                null, id, null, "RUNNING", name, "public",
                java.time.LocalDateTime.now().toString(), 0L);
    }

    public static ChatStreamEvent toolResult(String turnId, AtomicInteger seq,
                                            String id, String name, boolean ok,
                                            String preview, boolean truncated) {
        return new ChatStreamEvent(VERSION, TYPE_TOOL_RESULT, turnId, seq.incrementAndGet(),
                null, id, name, null, ok, preview, truncated, null,
                null, id, null, ok ? "SUCCEEDED" : "FAILED", name, "public",
                null, null);
    }

    public static ChatStreamEvent error(String turnId, AtomicInteger seq, String message) {
        return new ChatStreamEvent(VERSION, TYPE_ERROR, turnId, seq.incrementAndGet(),
                null, null, null, null, null, null, null, message,
                null, null, null, "FAILED", "执行失败", "public", null, null);
    }

    public static ChatStreamEvent done(String turnId, AtomicInteger seq) {
        return new ChatStreamEvent(VERSION, TYPE_DONE, turnId, seq.incrementAndGet(),
                null, null, null, null, null, null, null, null,
                null, null, null, "SUCCEEDED", "已完成", "public", null, null);
    }

    /** 将公开进度适配为 NDJSON v1 兼容事件。 */
    public static ChatStreamEvent publicProgress(
            String turnId, AtomicInteger seq, PublicProgressEvent event) {
        String type = switch (event.type()) {
            case ROUTE -> TYPE_ROUTE;
            case TOOL -> event.status() == AgentStepResult.Status.RUNNING
                    ? TYPE_TOOL_CALL : TYPE_TOOL_RESULT;
            case ERROR -> TYPE_ERROR;
            case DONE -> TYPE_DONE;
            default -> TYPE_STEP;
        };
        boolean terminalTool = TYPE_TOOL_RESULT.equals(type);
        Boolean ok = terminalTool ? event.status() == AgentStepResult.Status.SUCCEEDED : null;
        String preview = terminalTool || TYPE_STEP.equals(type) ? event.publicSummary() : null;
        String name = event.details().get("toolName") != null
                ? String.valueOf(event.details().get("toolName"))
                : event.details().get("actionId") != null
                ? String.valueOf(event.details().get("actionId"))
                : event.displayName();
        return new ChatStreamEvent(
                VERSION,
                type,
                turnId,
                seq.incrementAndGet(),
                null,
                event.stepId(),
                name,
                event.details(),
                ok,
                preview,
                Boolean.TRUE.equals(event.details().get("truncated")),
                TYPE_ERROR.equals(type) ? event.publicSummary() : null,
                event.runId(),
                event.stepId(),
                event.parentStepId(),
                event.status().name(),
                event.displayName(),
                "public",
                null,
                event.elapsedMs());
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
        String value = raw == null ? "" : raw;
        value = value.replaceAll(
                "(?i)(api[_-]?key|password|token|authorization)\\s*[:=]\\s*[^\\s,;]+",
                "$1=[REDACTED]");
        if (value.length() > 1000) {
            value = value.substring(0, 1000) + "[已截断]";
        }
        return Map.of("raw", value);
    }

    /** 工具参数只保留脱敏摘要，避免密钥和隐藏提示进入公开流。 */
    private static Object safeArgs(Object args) {
        if (!(args instanceof Map<?, ?> source)) {
            String value = args == null ? "" : String.valueOf(args);
            return value.length() <= 1000 ? value : value.substring(0, 1000) + "[已截断]";
        }
        Map<String, Object> safe = new LinkedHashMap<>();
        source.forEach((rawKey, value) -> {
            String key = String.valueOf(rawKey);
            String normalized = key.toLowerCase();
            if (!normalized.contains("password")
                    && !normalized.contains("token")
                    && !normalized.contains("secret")
                    && !normalized.contains("authorization")
                    && !normalized.contains("reasoning")
                    && !normalized.contains("systemprompt")) {
                safe.put(key, value);
            }
        });
        return safe;
    }
}
