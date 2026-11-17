package com.mychat.agent.quality;

import com.mychat.agent.api.AgentRunRequest;
import com.mychat.agent.api.AgentRunResult;

/** 不执行额外评价的质量策略。 */
public class NoopAgentQualityPolicy implements AgentQualityPolicy {

    private final String id;

    /** 创建指定 ID 的空质量策略。 */
    public NoopAgentQualityPolicy(String id) {
        this.id = id;
    }

    /** 返回策略 ID。 */
    @Override
    public String id() {
        return id;
    }

    /** 原样返回 Run 结果。 */
    @Override
    public AgentRunResult evaluate(AgentRunRequest request, AgentRunResult result) {
        return result;
    }
}
