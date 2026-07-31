package com.mychat.vo;

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

    @Schema(description = "关联知识库ID（null 表示普通会话）")
    @TableField(value = "kb_id")
    private String kbId;

    @Schema(description = "工作目录路径（null 表示使用默认 workspace）")
    @TableField(value = "work_dir")
    private String workDir;
}
