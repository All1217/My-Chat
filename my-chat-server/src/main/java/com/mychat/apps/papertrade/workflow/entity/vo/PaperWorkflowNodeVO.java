package com.mychat.apps.papertrade.workflow.entity.vo;

import lombok.Data;

/** 工作流蓝图节点（供前端 Vue Flow）。 */
@Data
public class PaperWorkflowNodeVO {
    private String id;
    private String label;
    /** start | process | ai | end */
    private String type;
    private Double x;
    private Double y;
}
