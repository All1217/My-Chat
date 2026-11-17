package com.mychat.apps.papertrade.audit.entity.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** Agent 工具写库审计行。 */
@TableName("paper_ai_tool_audit")
@Data
public class PaperAiToolAudit {

    @TableId(type = IdType.INPUT)
    private String id;

    @TableField("scenario_id")
    private String scenarioId;

    @TableField("agent_run_id")
    private String agentRunId;

    @TableField("tool_name")
    private String toolName;

    @TableField("table_name")
    private String tableName;

    private String op;

    private String symbol;

    @TableField("args_json")
    private String argsJson;

    @TableField("result_json")
    private String resultJson;

    private Boolean success;

    @TableField("error_message")
    private String errorMessage;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
