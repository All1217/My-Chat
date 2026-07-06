package com.mychat.entity.po;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "知识库")
@TableName(value = "knowledge_base")
@Data
public class KnowledgeBase {
    @Schema(description = "唯一标识")
    @TableId(type = IdType.INPUT)
    private String id;

    @Schema(description = "知识库名称")
    @TableField(value = "name")
    private String name;

    @Schema(description = "描述")
    @TableField(value = "description")
    private String description;

    @Schema(description = "创建时间")
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updatedAt;
}
