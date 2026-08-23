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

    @Schema(description = "入库切分目标 token 数")
    @TableField(value = "chunk_size")
    private Integer chunkSize = KnowledgeBaseSettings.DEFAULT_CHUNK_SIZE;

    @Schema(description = "相邻分片重叠 token 数")
    @TableField(value = "chunk_overlap")
    private Integer chunkOverlap = KnowledgeBaseSettings.DEFAULT_CHUNK_OVERLAP;

    @Schema(description = "检索返回片段上限")
    @TableField(value = "top_k")
    private Integer topK = KnowledgeBaseSettings.DEFAULT_TOP_K;

    @Schema(description = "检索相似度下限 0~1")
    @TableField(value = "similarity_threshold")
    private Double similarityThreshold = KnowledgeBaseSettings.DEFAULT_SIMILARITY_THRESHOLD;

    @Schema(description = "创建时间")
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updatedAt;
}
