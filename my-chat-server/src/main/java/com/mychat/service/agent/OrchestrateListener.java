package com.mychat.service.agent;

import com.mychat.vo.OrchestrateStepVO;

/**
 * 编排逐步回调接口（函数式）。
 * <p>
 * {@link AgentOrchestratorService#orchestrate} 每完成一步（含 finish）调用一次 {@link #onStep}，
 * 供主聊天把步骤推成 NDJSON {@code step}；调试同步 API 可不传（listener=null）。
 */
@FunctionalInterface
public interface OrchestrateListener {

    /**
     * 单步完成时回调。
     *
     * @param step 已完成步骤（含 action / instruction / observation；finish 时 observation 为最终答复）
     */
    void onStep(OrchestrateStepVO step);
}
