package com.mychat.agent.quality;

import com.mychat.agent.api.AgentRunRequest;
import com.mychat.agent.api.AgentRunResult;

/** Agent Run 完成后的场景质量策略。 */
public interface AgentQualityPolicy {

    /** 策略 ID。 */
    String id();

    /** 评价结果并返回附带质量指标的新结果。 */
    AgentRunResult evaluate(AgentRunRequest request, AgentRunResult result);
}
