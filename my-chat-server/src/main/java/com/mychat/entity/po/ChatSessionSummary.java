package com.mychat.entity.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 编排读路径用的会话滚动摘要（较早轮次压缩）。
 */
@Data
@TableName("chat_session_summary")
public class ChatSessionSummary {

    @TableId("conversation_id")
    private String conversationId;

    @TableField("summary_text")
    private String summaryText;

    @TableField("covered_until_sequence_id")
    private Long coveredUntilSequenceId;

    @TableField("updated_at")
    private OffsetDateTime updatedAt;
}
