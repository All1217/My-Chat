package com.mychat.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 任务内质量环单轮记录（生成 → 读回 → 规则/评价）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EvaluateOptimizeRoundVO {

    /** 从 1 开始的迭代号 */
    private int iteration;

    /** 生成侧摘要（模型回复或错误信息） */
    private String generatorSummary;

    /** 读回文件内容截断快照 */
    private String fileSnapshot;

    /** Java 硬规则是否通过 */
    private boolean ruleCheckPassed;

    /** LLM 评价是否通过；硬规则失败时为 false */
    private boolean evaluationPass;

    /** 给下一轮生成器的修改意见 */
    private String feedback;

    /** 评价/规则失败理由 */
    private String reasoning;
}
