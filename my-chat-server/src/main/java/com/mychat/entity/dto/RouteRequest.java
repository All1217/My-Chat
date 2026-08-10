package com.mychat.entity.dto;

import lombok.Data;

/**
 * Routing 调试 API 请求体（{@code POST /ai/agent/route} NDJSON）。
 * <p>
 * {@code kbId} / {@code workDir} 作为会话约束提示分类器；无 kbId 时不可落到 kb 路由。
 */
@Data
public class RouteRequest {

    /** 用户输入文本（必填） */
    private String input;

    /** 知识库 ID（可选；有则允许 kb 路由） */
    private String kbId;

    /** 会话工作目录（可选；提示 file 路由，缺省用默认工作区） */
    private String workDir;

    /**
     * 可选会话 ID，供本轮 {@code MessageChatMemoryAdvisor} 使用。
     * 缺省为临时 {@code agent-route-{uuid}}（旁路不落 chat_assistant_turns）。
     */
    private String chatId;

    /**
     * 是否尝试质量环；Demo 侧默认忽略（仅打日志）。
     * 写盘质量环请用主聊天或 {@code /ai/agent/evaluate-optimize}。
     */
    private Boolean qualityLoop;

    /** 质量环评价标准（可选；Demo 当前不消费） */
    private String criteria;
}
