package com.mychat.apps.papertrade.agent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** 交易评估员类型化输出。 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StageEvalBatch(
        String summaryText,
        String strategyAdvice,
        String emailBody,
        List<String> evidenceRefs
) {
    public StageEvalBatch {
        evidenceRefs = evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs);
    }
}
