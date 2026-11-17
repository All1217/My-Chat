package com.mychat.apps.papertrade.workflow.entity.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 最近一次运行摘要（叠加热力到蓝图）。 */
@Data
public class PaperWorkflowLatestRunVO {
    private String runId;
    private String status;
    private String createdAt;
    private String finishedAt;
    private String errorMessage;
    private String agentRunId;
    private List<PaperWorkflowStepStatusVO> domainSteps = new ArrayList<>();
    private List<PaperWorkflowAgentStepVO> agentSteps = new ArrayList<>();
}
