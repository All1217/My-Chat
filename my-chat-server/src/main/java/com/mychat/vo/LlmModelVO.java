package com.mychat.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

/** 模型配置对外视图：密钥已脱敏。 */
@Data
public class LlmModelVO {
    private String id;
    private String name;
    private String provider;
    private String baseUrl;
    private String apiKeyMasked;
    private String modelId;
    private Integer maxTokens;
    private Boolean enabled;

    @JsonProperty("isDefault")
    private Boolean isDefault;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updatedAt;
}
