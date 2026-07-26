package com.mychat.entity.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 与前端 {@code ToolMessagePart} 同构的片段，写入 {@code chat_assistant_turns.parts}。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "助手消息片段（工具时间线）")
public class MessagePartVO {

    @Schema(description = "片段类型，回放场景固定为 tool")
    private String type;

    @Schema(description = "工具调用 id，与 tool_call / tool_result 对齐")
    private String id;

    @Schema(description = "工具名，如 ls / cat")
    private String name;

    @Schema(description = "调用参数")
    private Object args;

    @Schema(description = "running | done | error | cancelled")
    private String status;

    @Schema(description = "结果预览（已 unwrap）")
    private String resultPreview;

    @Schema(description = "工具是否成功")
    private Boolean ok;

    @Schema(description = "预览是否被截断")
    private Boolean truncated;
}
