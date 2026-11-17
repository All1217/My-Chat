package com.mychat.agent.trace;

import com.mychat.agent.api.AgentStepResult;
import com.mychat.agent.api.PublicProgressEvent;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** 将内部 Trace 转为不泄露推理、系统提示和工具原文的公开进度。 */
@Component
public class PublicProgressAdapter {

    private static final int SUMMARY_LIMIT = 1_000;
    private static final Set<String> PUBLIC_DETAIL_KEYS = Set.of(
            "toolName", "citations", "kbScope", "truncated", "actionId");
    private static final Pattern SECRET = Pattern.compile(
            "(?i)(api[_-]?key|password|token|authorization)\\s*[:=]\\s*[^\\s,;]+");

    /** 转换并执行字段白名单与文本脱敏。 */
    public PublicProgressEvent adapt(AgentTraceEvent event) {
        Map<String, Object> details = new LinkedHashMap<>();
        event.metadata().forEach((key, value) -> {
            if (PUBLIC_DETAIL_KEYS.contains(key)) {
                details.put(key, value);
            }
        });
        if (!event.citations().isEmpty()) {
            details.put("citations", event.citations());
        }
        String summary = event.status() == AgentStepResult.Status.FAILED
                ? safe(event.errorMessage()) : safe(event.outputSummary());
        return new PublicProgressEvent(
                event.runId(),
                event.stepId(),
                event.parentStepId(),
                publicType(event),
                event.status(),
                displayName(event),
                summary,
                Math.max(0, event.elapsedMs()),
                details);
    }

    /** 映射内部 Span 类型。 */
    private PublicProgressEvent.Type publicType(AgentTraceEvent event) {
        if (event.status() == AgentStepResult.Status.FAILED) {
            return PublicProgressEvent.Type.ERROR;
        }
        return switch (event.type()) {
            case TOOL -> PublicProgressEvent.Type.TOOL;
            case GUARDRAIL -> PublicProgressEvent.Type.GUARDRAIL;
            case EVALUATOR -> PublicProgressEvent.Type.QUALITY;
            default -> PublicProgressEvent.Type.STEP;
        };
    }

    /** 生成安全展示名。 */
    private String displayName(AgentTraceEvent event) {
        return switch (event.type()) {
            case MODEL -> "模型调用";
            case TOOL -> event.name().isBlank() ? "工具调用" : event.name();
            case GUARDRAIL -> "安全检查";
            case HANDOFF -> "任务移交";
            case EVALUATOR -> "质量评估";
            case APPROVAL -> "等待批准";
            default -> event.name().isBlank() ? "执行步骤" : event.name();
        };
    }

    /** 对公开摘要做密钥掩码和长度限制。 */
    private String safe(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String redacted = SECRET.matcher(value).replaceAll("$1=[REDACTED]");
        return redacted.length() <= SUMMARY_LIMIT
                ? redacted : redacted.substring(0, SUMMARY_LIMIT) + "[已截断]";
    }
}
