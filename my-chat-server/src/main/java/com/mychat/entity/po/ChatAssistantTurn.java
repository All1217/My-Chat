package com.mychat.entity.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.mychat.entity.vo.MessagePartVO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 每轮 ASSISTANT 的 UI 轨迹（进阶 3 · 第 3 周）。
 * <p>
 * 逻辑外键 {@code conversationId} → {@code chat_sessions.conversation_id}；
 * 不修改 {@code spring_ai_chat_memory}。
 */
@Schema(description = "助手回合 UI 轨迹")
@TableName(value = "chat_assistant_turns", autoResultMap = true)
@Data
public class ChatAssistantTurn {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("conversation_id")
    private String conversationId;

    /** 与 NDJSON 流 turnId 一致 */
    @TableField("turn_id")
    private String turnId;

    /** 会话内 ASSISTANT 序号（0-based），用于与 Memory 对齐 */
    @TableField("assistant_ordinal")
    private Integer assistantOrdinal;

    /** 本轮正文快照，读历史时精确匹配兜底 */
    @TableField("assistant_text")
    private String assistantText;

    @TableField("thinking")
    private String thinking;

    @TableField(value = "parts", typeHandler = JacksonTypeHandler.class)
    private List<MessagePartVO> parts;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
