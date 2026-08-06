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

    /** 交给 Worker 的子任务；finish 时可为最终答复草稿 */
    private String instruction;

    /** Worker 观察结果（已截断）；finish 步可为空 */
    private String observation;
}
