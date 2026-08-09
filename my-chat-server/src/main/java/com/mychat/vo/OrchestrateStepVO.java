package com.mychat.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Orchestrator 单步执行记录（调试回传 / 后续可映射为 NDJSON step）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
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
}
