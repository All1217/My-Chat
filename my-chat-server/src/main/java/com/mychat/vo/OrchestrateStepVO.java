package com.mychat.vo;

import com.mychat.entity.dto.KnowledgeRetrieveHit;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Orchestrator 单步执行记录（调试回传 / 主聊天映射为 NDJSON step）。
 */
@Data
@NoArgsConstructor
public class OrchestrateStepVO {

    /** 从 1 开始的步号 */
    private int index;

    /** retrieve_kb / file / search / general / finish / invalid */
    private String action;

    /** 编排器给出的理由 */
    private String reasoning;

    /** 交给 Worker 的子任务；finish 时为编排器给出的最终答复草稿（可能是提纲） */
    private String instruction;

    /**
     * Worker 观察（编排历史侧可较长）；finish 步为已决议的用户可见最终答复
     * （与主气泡 / Memory 一致，可能由 observation 合成兜底产生）。
     */
    private String observation;

    /**
     * 知识库检索步的结构化来源（文件名等）；其它 action 为空。
     * 经 NDJSON {@code step.args.citations} 落入 parts，供气泡引用标签回放。
     */
    private List<KnowledgeRetrieveHit> citations;

    /**
     * 五参构造：保持现有调用点不变，citations 用 setter 另设。
     */
    public OrchestrateStepVO(
            int index, String action, String reasoning, String instruction, String observation) {
        this.index = index;
        this.action = action;
        this.reasoning = reasoning;
        this.instruction = instruction;
        this.observation = observation;
    }
}
