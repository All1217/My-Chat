package com.mychat.agent.dto;

import lombok.Data;

/**
 * Routing 调试 API 请求体。
 * <p>
 * {@code kbId} 仅在分类结果为 {@code kb} 时必填；其它路由可省略。
 */
@Data
public class RouteRequest {

    /** 用户输入文本（必填） */
    private String input;

    /** 知识库 ID；路由为 kb 时必填 */
    private String kbId;
}
