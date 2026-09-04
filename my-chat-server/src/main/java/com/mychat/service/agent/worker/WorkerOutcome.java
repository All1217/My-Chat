package com.mychat.service.agent.worker;

import com.mychat.entity.dto.KnowledgeRetrieveHit;

import java.util.List;

/**
 * 单步 Worker 结果：观察文本，知识库步可带 citations。
 */
public record WorkerOutcome(String observation, List<KnowledgeRetrieveHit> citations) {

    /** 无引用的纯文本观察。 */
    public static WorkerOutcome text(String observation) {
        return new WorkerOutcome(observation != null ? observation : "", null);
    }
}
