package com.mychat.entity.dto;

import lombok.Data;

import java.util.List;

/**
 * 任务内质量环请求（Evaluator-Optimizer 调试 API）。
 * <p>
 * 用于单次写盘任务的 generate → evaluate → refine；不是离线评测题集入参。
 */
@Data
public class EvaluateOptimizeRequest {

    /** 生成目标描述（必填） */
    private String goal;

    /** 相对工作目录的目标文件路径（必填） */
    private String path;

    /** 质量标准（必填，供 LLM 评价） */
    private String criteria;

    /** 可选硬门禁：文件内容须全部包含这些子串 */
    private List<String> mustContain;

    /** 工作目录绝对路径（可选；默认应用工作区根） */
    private String workDir;

    /** 最大迭代次数（可选，默认 3，服务端钳制到 [1, 5]） */
    private Integer maxIterations;
}
