package com.mychat.agent.trace;

import com.mychat.agent.api.AgentStepResult;
import com.mychat.agent.api.PublicProgressEvent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 公开进度脱敏契约测试。 */
class PublicProgressAdapterTest {

    /** reasoning、system、原始工具结果和密钥不得进入公开事件。 */
    @Test
    void shouldExposeOnlySafePublicFields() {
        AgentTraceEvent trace = new AgentTraceEvent(
                "run-1",
                "tool-1",
                "step-1",
                AgentTraceEvent.Type.TOOL,
                AgentStepResult.Status.SUCCEEDED,
                "database",
                "hidden system prompt",
                "查询完成 api_key=secret",
                List.of(Map.of("filename", "guide.md")),
                25,
                null,
                Map.of(
                        "toolName", "database",
                        "kbScope", "vector",
                        "reasoning", "private thought",
                        "systemPrompt", "hidden",
                        "rawToolResult", "sensitive rows"));

        PublicProgressEvent event = new PublicProgressAdapter().adapt(trace);

        assertThat(event.publicSummary()).contains("[REDACTED]").doesNotContain("secret");
        assertThat(event.details()).containsKeys("toolName", "kbScope", "citations");
        assertThat(event.details()).doesNotContainKeys(
                "reasoning", "systemPrompt", "rawToolResult");
        assertThat(event.type()).isEqualTo(PublicProgressEvent.Type.TOOL);
    }
}
