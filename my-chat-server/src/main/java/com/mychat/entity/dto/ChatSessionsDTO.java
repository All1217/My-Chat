package com.mychat.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "聊天会话DTO")
@Data
public class ChatSessionsDTO {
    @Schema(description = "唯一标识")
    private String conversationId;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "标题")
    private String title;
}
