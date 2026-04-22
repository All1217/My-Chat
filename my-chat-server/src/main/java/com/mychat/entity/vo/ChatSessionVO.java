package com.mychat.entity.vo;

import com.baomidou.mybatisplus.annotation.TableField;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "会话VO")
public class ChatSessionVO {
    @Schema(description = "会话标题")
    @TableField(value = "title")
    private String title;

    @Schema(description = "会话ID")
    @TableField(value = "conversation_id")
    private String conversationId;
}
