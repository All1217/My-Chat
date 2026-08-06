package com.mychat.entity.dto;

import lombok.Data;

/**
 * Orchestrator-Workers 调试 API 请求体。
 * <p>
 * {@code kbId} 有值时才允许 {@code retrieve_kb}；{@code maxSteps} 由服务端钳制到 [1, 8]。
 */
@Data
public class OrchestrateRequest {

    /** 用户任务描述（必填） */
    private String input;

    /** 知识库 ID（可选；无则禁止 retrieve_kb） */
    private String kbId;

    /** 工作目录（可选；file/search 时优先使用） */
    private String workDir;

    /** 最大编排步数（可选，默认 6） */
    private Integer maxSteps;
}
