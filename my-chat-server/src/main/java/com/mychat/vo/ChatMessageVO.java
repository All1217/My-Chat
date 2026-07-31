package com.mychat.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 历史消息 VO：在 Spring AI Memory 文本之上附加 thinking / parts，供前端时间线回放。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "聊天历史消息（含可选工具轨迹）")
public class ChatMessageVO {

    @Schema(description = "消息角色：USER / ASSISTANT / SYSTEM / …")
    private String messageType;

    @Schema(description = "消息正文")
    private String text;

    @Schema(description = "思考过程（可空）")
    private String thinking;

    @Schema(description = "工具时间线等片段（可空）")
    private List<MessagePartVO> parts;
}
