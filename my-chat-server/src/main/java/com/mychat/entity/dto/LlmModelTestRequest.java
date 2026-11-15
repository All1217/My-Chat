package com.mychat.entity.dto;

import lombok.Data;

/**
 * 测通请求：有 id 则用已存配置（apiKey 非 null 时覆盖，空白则测环境变量回退）；
 * 无 id 则用表单字段测未保存配置。
 */
@Data
public class LlmModelTestRequest {
    private String id;
    private String provider;
    private String baseUrl;
    private String apiKey;
    private String modelId;
    private Integer maxTokens;
}
