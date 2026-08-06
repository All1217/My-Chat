package com.mychat.vo;

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

    @Schema(description = "片段类型：tool | route")
    private String type;

    @Schema(description = "工具调用 id，或 route 片段 id")
    private String id;

    @Schema(description = "工具名（ls/cat…）或路由标签（file/kb/search/general）")
    private String name;

    @Schema(description = "调用参数")
    private Object args;

    @Schema(description = "running | done | error | cancelled")
    private String status;

    @Schema(description = "工具结果预览，或路由分类理由")
    private String resultPreview;

    @Schema(description = "工具是否成功")
    private Boolean ok;

    @Schema(description = "预览是否被截断")
    private Boolean truncated;
}
