package com.mychat.entity.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档切段原文与摘要：入库与向量双写，供只读分段列表展示。
 */
@Schema(description = "文档切段（只读展示）")
@TableName(value = "document_chunk")
@Data
public class DocumentChunk {

    @Schema(description = "与向量段同一套 nameUUID")
    @TableId(type = IdType.INPUT)
    private String id;

    @Schema(description = "所属文档 ID")
    @TableField(value = "document_id")
    private String documentId;

    @Schema(description = "所属知识库 ID")
    @TableField(value = "kb_id")
    private String kbId;

    @Schema(description = "切段下标，从 0 起")
    @TableField(value = "position")
    private Integer position;

    @Schema(description = "切段原文")
    @TableField(value = "content")
    private String content;

    @Schema(description = "chunk 摘要，失败可空")
    @TableField(value = "summary")
    private String summary;

    @Schema(description = "写入时间")
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
