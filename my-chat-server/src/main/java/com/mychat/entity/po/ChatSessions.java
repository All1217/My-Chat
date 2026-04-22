package com.mychat.entity.po;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.sql.Date;
import java.sql.Timestamp;

@Schema(description = "聊天会话表")
@TableName(value = "chat_sessions")
@Data
public class ChatSessions {
    @Schema(description = "唯一标识")
    @TableId(type = IdType.INPUT)
    private String conversationId;

    @Schema(description = "用户ID")
    @TableField(value = "user_id")
    private Long userId;

    @Schema(description = "标题")
    @TableField(value = "title")
    private String title;

    @Schema(description = "创建时间")
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createdAt;

    @Schema(description = "更新时间")
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updatedAt;
}
