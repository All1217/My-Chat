package com.mychat.entity.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/** 新建或更新一条对话模型配置。 */
@Data
public class LlmModelUpsertRequest {
    /** 更新时必填；创建时忽略 */
    private String id;

    /** 展示名称 */
    private String name;

    /** 供应商预设键 */
    private String provider;

    /** OpenAI 兼容根地址 */
    private String baseUrl;

    /** 密钥；更新时留空表示保持原值 */
    private String apiKey;

    /** 供应商侧模型名 */
    private String modelId;

    /** 单次生成 token 上限 */
    private Integer maxTokens;

    /** 是否启用 */
    private Boolean enabled;

    /** 创建时可选：是否设为全局默认 */
    @JsonProperty("isDefault")
    private Boolean isDefault;
}
