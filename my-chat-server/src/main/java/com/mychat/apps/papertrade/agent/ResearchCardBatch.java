package com.mychat.apps.papertrade.agent;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.List;

/** 搜集员类型化输出契约。 */
public record ResearchCardBatch(List<ResearchCardDecision> cards) {

    /** 固化卡片集合。 */
    public ResearchCardBatch {
        cards = cards == null ? List.of() : List.copyOf(cards);
    }

    /** 单个候选研究卡契约。 */
    public record ResearchCardDecision(
            String symbol,
            String name,
            boolean kept,
            String story,
            List<String> risks,
            String rejectReason,
            double fitScore,
            double evidenceScore,
            List<String> signalTags,
            @JsonDeserialize(using = FlexibleStringListDeserializer.class)
            List<String> evidenceRefs) {

        /** 固化列表字段。 */
        public ResearchCardDecision {
            risks = risks == null ? List.of() : List.copyOf(risks);
            signalTags = signalTags == null ? List.of() : List.copyOf(signalTags);
            evidenceRefs = evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs);
        }
    }
}

