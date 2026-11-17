package com.mychat.agent.runtime;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** AnswerPolicy 注册表。 */
@Component
public class AnswerPolicyRegistry {

    private final Map<String, AnswerPolicy> policies;

    /** 注册并校验策略 ID 唯一。 */
    public AnswerPolicyRegistry(List<AnswerPolicy> policies) {
        Map<String, AnswerPolicy> indexed = new LinkedHashMap<>();
        for (AnswerPolicy policy : policies) {
            if (indexed.putIfAbsent(policy.id(), policy) != null) {
                throw new IllegalStateException("Duplicate answer policy: " + policy.id());
            }
        }
        this.policies = Map.copyOf(indexed);
    }

    /** 获取策略。 */
    public AnswerPolicy require(String id) {
        String effectiveId = id == null || id.isBlank() ? "direct" : id;
        AnswerPolicy policy = policies.get(effectiveId);
        if (policy == null) {
            throw new IllegalArgumentException("Unknown answer policy: " + effectiveId);
        }
        return policy;
    }
}
