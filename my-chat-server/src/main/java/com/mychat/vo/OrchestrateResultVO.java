package com.mychat.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrator-Workers 调试 API 响应：步骤轨迹 + 最终答案。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrchestrateResultVO {

    /** 汇总给用户的最终文本 */
    private String finalAnswer;

    /** finish | max_steps */
    private String finishedReason;

    /** 已执行步骤（含 finish 决策步时 observation 可为空） */
    private List<OrchestrateStepVO> steps = new ArrayList<>();
}
