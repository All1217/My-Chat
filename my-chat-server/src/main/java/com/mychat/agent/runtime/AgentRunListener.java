package com.mychat.agent.runtime;

import com.mychat.agent.api.AgentRunRequest;
import com.mychat.agent.api.AgentRunResult;
import com.mychat.agent.api.AgentScenarioProfile;
import com.mychat.agent.api.AgentStepResult;

/** Run/step 生命周期监听器；Phase 3 可替换为持久化 Span。 */
public interface AgentRunListener {

    /** Run 开始。 */
    default void onRunStarted(AgentRunRequest request, AgentScenarioProfile profile) {
    }

    /** Step 开始。 */
    default void onStepStarted(AgentRunRequest request, AgentDecision decision, int stepIndex) {
    }

    /** Step 完成。 */
    default void onStepCompleted(AgentRunRequest request, AgentStepResult result) {
    }

    /** Run 结束。 */
    default void onRunCompleted(AgentRunRequest request, AgentRunResult result) {
    }
}
