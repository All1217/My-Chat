package com.mychat.entity.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.sql.Timestamp;

@Schema(description = "聊天记忆表")
@TableName(value = "spring_ai_chat_memory")
@Data
public class SpringAiChatMemory {
    @Schema(description = "唯一标识")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "会话ID")
    @TableField(value = "conversation_id")
    private String conversationId;

    @Schema(description = "会话类型")
    @TableField(value = "type")
    private String type;

    @Schema(description = "这条记录的内容")
    @TableField(value = "content")
    private String content;

    @Schema(description = "这条记录生成时候的时间戳")
    @TableField(value = "timestamp")
    private Timestamp timestamp;
}
