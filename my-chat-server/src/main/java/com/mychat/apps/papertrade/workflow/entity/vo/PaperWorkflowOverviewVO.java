package com.mychat.apps.papertrade.workflow.entity.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 可视化工作流总览（搜集员 + 交易员）。 */
@Data
public class PaperWorkflowOverviewVO {
    private List<PaperWorkflowGraphVO> graphs = new ArrayList<>();
}
