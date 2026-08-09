package com.mychat.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 任务内质量环响应：是否达标、结束原因、最后读回内容与各轮轨迹。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EvaluateOptimizeResultVO {

    /** 是否最终达标 */
    private boolean passed;

    /** passed | max_iterations */
    private String finishedReason;

    /** 目标相对路径 */
    private String path;

    /** 最后一轮读回全文（可能截断提示） */
    private String finalContent;

    /** 各轮迭代轨迹 */
    private List<EvaluateOptimizeRoundVO> rounds = new ArrayList<>();
}
