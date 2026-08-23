package com.mychat.entity.po;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "文档元数据")
@TableName(value = "document_meta")
@Data
public class DocumentMeta {

    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_READY = "READY";
    public static final String STATUS_FAILED = "FAILED";
    @Schema(description = "唯一标识")
    @TableId(type = IdType.INPUT)
    private String id;

    @Schema(description = "所属知识库ID")
    @TableField(value = "kb_id")
    private String kbId;

    @Schema(description = "文件名")
    @TableField(value = "filename")
    private String filename;

    @Schema(description = "文件大小（字节）")
    @TableField(value = "file_size")
    private Long fileSize;

    @Schema(description = "文件类型")
    @TableField(value = "file_type")
    private String fileType;

    @Schema(description = "分片数量")
    @TableField(value = "chunk_count")
    private Integer chunkCount;

    @Schema(description = "状态: PROCESSING / READY / FAILED")
    @TableField(value = "status")
    private String status;

    @Schema(description = "原始文件落盘路径")
    @JsonIgnore
    @TableField(value = "storage_path")
    private String storagePath;

    @Schema(description = "入库失败原因")
    @TableField(value = "error_message")
    private String errorMessage;

    @Schema(description = "创建时间")
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updatedAt;
}
