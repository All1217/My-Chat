package com.mychat.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Routing 调试 API 响应：分类标签 + 推理 + 专用路径的最终回答。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RouteResultVO {

    /** 路由标签：file / kb / search / general */
    private String route;

    /** 分类器给出的简要理由 */
    private String reasoning;

    /** 选定路径处理后的回答文本 */
    private String answer;
}
