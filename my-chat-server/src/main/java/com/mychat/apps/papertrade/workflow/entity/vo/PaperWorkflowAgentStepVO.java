package com.mychat.apps.papertrade.workflow.entity.vo;

import lombok.Data;

/** 最近一轮 agent.steps 摘要。 */
@Data
public class PaperWorkflowAgentStepVO {
    private Integer index;
    private String action;
    private String status;
    private String observation;
}
