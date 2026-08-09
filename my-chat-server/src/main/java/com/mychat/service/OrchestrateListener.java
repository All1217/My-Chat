package com.mychat.service;

import com.mychat.vo.OrchestrateStepVO;

/**
 * Orchestrator 逐步回调（供主聊天 NDJSON 推送 {@code step} 事件）。
 * <p>
 * 调试 API 可不传 listener；主路传入后每完成一步（含 finish）回调一次。
 */
@FunctionalInterface
public interface OrchestrateListener {

    /**
     * @param step 已完成的步骤（含 observation，finish 步 observation 可空）
     */
    void onStep(OrchestrateStepVO step);
}
