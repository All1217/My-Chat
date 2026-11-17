package com.mychat.common;

import com.mychat.agent.api.AgentStepResult;
import com.mychat.agent.api.PublicProgressEvent;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** NDJSON v1 的 Phase 3 向后兼容测试。 */
class ChatStreamEventPhase3Test {

    /** 旧字段保持不变，新增状态字段可选且不公开 reasoning。 */
    @Test
    void stepShouldRemainV1AndHideReasoning() {
        ChatStreamEvent event = ChatStreamEvent.step(
                "turn-1", new AtomicInteger(), 2, "search",
                "private reasoning", "search task", "safe result");

        assertThat(event.v()).isEqualTo(1);
        assertThat(event.type()).isEqualTo("step");
        assertThat(event.id()).isEqualTo("step-2");
        assertThat(event.name()).isEqualTo("search");
        assertThat(event.text()).isNull();
        assertThat(event.stepId()).isEqualTo("step-2");
        assertThat(event.status()).isEqualTo("SUCCEEDED");
        assertThat(event.visibility()).isEqualTo("public");
    }

    /** 工具开始和结果沿用原 type，并通过相同 ID 原位更新。 */
    @Test
    void toolEventsShouldShareStableStepId() {
        AtomicInteger sequence = new AtomicInteger();
        ChatStreamEvent call = ChatStreamEvent.toolCall(
                "turn-1", sequence, "tool-1", "searchWeb", java.util.Map.of());
        ChatStreamEvent result = ChatStreamEvent.toolResult(
                "turn-1", sequence, "tool-1", "searchWeb", true, "ok", false);

        assertThat(call.type()).isEqualTo("tool_call");
        assertThat(result.type()).isEqualTo("tool_result");
        assertThat(call.stepId()).isEqualTo(result.stepId()).isEqualTo("tool-1");
        assertThat(call.status()).isEqualTo("RUNNING");
        assertThat(result.status()).isEqualTo("SUCCEEDED");
    }

    /** 公开进度可映射为带父子关系的 v1 工具事件。 */
    @Test
    void shouldAdaptPublicProgressWithoutNewEventType() {
        PublicProgressEvent progress = new PublicProgressEvent(
                "run-1", "tool-1", "step-1",
                PublicProgressEvent.Type.TOOL,
                AgentStepResult.Status.RUNNING,
                "网页搜索", "", 8,
                java.util.Map.of("toolName", "searchWeb"));

        ChatStreamEvent event = ChatStreamEvent.publicProgress(
                "turn-1", new AtomicInteger(), progress);

        assertThat(event.type()).isEqualTo("tool_call");
        assertThat(event.runId()).isEqualTo("run-1");
        assertThat(event.stepId()).isEqualTo("tool-1");
        assertThat(event.parentStepId()).isEqualTo("step-1");
        assertThat(event.text()).isNull();
    }

    /** Runtime 动作开始和完成使用同一 stepId，供前端原位更新。 */
    @Test
    void runtimeActionShouldKeepStableStepId() {
        AtomicInteger sequence = new AtomicInteger();
        PublicProgressEvent started = new PublicProgressEvent(
                "run-1", "run-1-step-1", null,
                PublicProgressEvent.Type.STEP,
                AgentStepResult.Status.RUNNING,
                "文件处理", "", 0,
                java.util.Map.of("actionId", "file"));
        PublicProgressEvent completed = new PublicProgressEvent(
                "run-1", "run-1-step-1", null,
                PublicProgressEvent.Type.STEP,
                AgentStepResult.Status.SUCCEEDED,
                "文件处理", "written", 12,
                java.util.Map.of("actionId", "file"));

        ChatStreamEvent first = ChatStreamEvent.publicProgress("turn-1", sequence, started);
        ChatStreamEvent last = ChatStreamEvent.publicProgress("turn-1", sequence, completed);

        assertThat(first.stepId()).isEqualTo(last.stepId()).isEqualTo("run-1-step-1");
        assertThat(first.status()).isEqualTo("RUNNING");
        assertThat(last.status()).isEqualTo("SUCCEEDED");
    }
}
