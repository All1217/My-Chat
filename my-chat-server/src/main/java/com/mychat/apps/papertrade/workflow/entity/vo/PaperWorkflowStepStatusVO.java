package com.mychat.apps.papertrade.workflow.entity.vo;

import lombok.Data;

/** 最近一轮 domainStep 状态。 */
@Data
public class PaperWorkflowStepStatusVO {
    private String step;
    private String status;
    private String summary;
    private String error;
}
