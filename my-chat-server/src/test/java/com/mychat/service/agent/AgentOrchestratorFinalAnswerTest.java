package com.mychat.service.agent;

import com.mychat.service.agent.workflow.OrchestratorWorkflow;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 编排循环门控：单步快路径与 complexity 规范化（不依赖 Spring 上下文）。
 * Worker 消息拼装见 {@link com.mychat.service.agent.worker.WorkerPromptSupportTest}。
 */
class AgentOrchestratorFinalAnswerTest {

    @Test
    void normalizeComplexityDefaultsToMulti() {
        assertEquals("multi", OrchestratorWorkflow.normalizeComplexity(null));
        assertEquals("multi", OrchestratorWorkflow.normalizeComplexity(""));
        assertEquals("multi", OrchestratorWorkflow.normalizeComplexity("unknown"));
        assertEquals("single", OrchestratorWorkflow.normalizeComplexity("single"));
        assertEquals("single", OrchestratorWorkflow.normalizeComplexity("SINGLE"));
        assertEquals("multi", OrchestratorWorkflow.normalizeComplexity("multi"));
    }

    @Test
    void shouldAutoFinishOnlyForSuccessfulSingleShot() {
        assertTrue(AgentOrchestratorService.shouldAutoFinishAfterWorker(
                "single", "## 封装\n隐藏实现细节"));
        assertFalse(AgentOrchestratorService.shouldAutoFinishAfterWorker(
                "multi", "## 封装\n隐藏实现细节"));
        assertFalse(AgentOrchestratorService.shouldAutoFinishAfterWorker(
                "single", "[约束] 未提供 kbId"));
        assertFalse(AgentOrchestratorService.shouldAutoFinishAfterWorker(
                "single", "[Worker 错误] timeout"));
        assertFalse(AgentOrchestratorService.shouldAutoFinishAfterWorker("single", "  "));
    }

}
