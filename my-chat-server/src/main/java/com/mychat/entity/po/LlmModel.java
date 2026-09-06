package com.mychat.entity.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/** OpenAI 兼容对话模型目录行。 */
@TableName(value = "llm_model")
@Data
public class LlmModel {
    @TableId(type = IdType.INPUT)
    private String id;

    @TableField(value = "name")
    private String name;

    @TableField(value = "provider")
    private String provider;

    @TableField(value = "base_url")
    private String baseUrl;

    @TableField(value = "api_key")
    private String apiKey;

    @TableField(value = "model_id")
    private String modelId;

    @TableField(value = "max_tokens")
    private Integer maxTokens;

    @TableField(value = "enabled")
    private Boolean enabled;

    @TableField(value = "is_default")
    private Boolean isDefault;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updatedAt;
}
