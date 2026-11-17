package com.mychat.agent.quality;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 按 Profile qualityPolicy ID 解析质量策略。 */
@Component
public class AgentQualityPolicyRegistry {

    private final Map<String, AgentQualityPolicy> policies;

    /** 注册并校验质量策略 ID 唯一。 */
    public AgentQualityPolicyRegistry(List<AgentQualityPolicy> policies) {
        Map<String, AgentQualityPolicy> indexed = new LinkedHashMap<>();
        for (AgentQualityPolicy policy : policies) {
            if (indexed.putIfAbsent(policy.id(), policy) != null) {
                throw new IllegalStateException("重复 Agent QualityPolicy: " + policy.id());
            }
        }
        this.policies = Map.copyOf(indexed);
    }

    /** 获取指定质量策略。 */
    public AgentQualityPolicy require(String id) {
        AgentQualityPolicy policy = policies.get(id);
        if (policy == null) {
            throw new IllegalArgumentException("未注册 Agent QualityPolicy: " + id);
        }
        return policy;
    }
}
