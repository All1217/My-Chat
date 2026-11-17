package com.mychat.apps.papertrade.workflow.entity.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 一套只读工作流图：蓝图 + 最近运行。 */
@Data
public class PaperWorkflowGraphVO {
    private String id;
    private String title;
    private List<PaperWorkflowNodeVO> nodes = new ArrayList<>();
    private List<PaperWorkflowEdgeVO> edges = new ArrayList<>();
    private PaperWorkflowLatestRunVO latest;
}
