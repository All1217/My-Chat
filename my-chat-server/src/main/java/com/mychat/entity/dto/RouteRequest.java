package com.mychat.entity.dto;

import lombok.Data;

/**
 * Routing 调试 API 请求体。
 * <p>
 * {@code kbId} / {@code workDir} 作为会话约束提示分类器；无 kbId 时不可落到 kb 路由。
 */
@Data
public class RouteRequest {

    /** 用户输入文本（必填） */
    private String input;

    /** 知识库 ID（可选；有则允许 kb 路由） */
    private String kbId;

    /** 会话工作目录（可选；提示 file 路由） */
    private String workDir;
}
