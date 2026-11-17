package com.mychat.apps.papertrade.trader.entity.vo;

import lombok.Data;

import java.util.List;

/** AI 轮次详情。 */
@Data
public class PaperAiRunVO {
    private String id;
    private String jobId;
    private String status;
    private String contextJson;
    private String rawResponse;
    private String errorMessage;
    private String frameworkTrace;
    /** 关联 agent_run.id（business_ref 反查）。 */
    private String agentRunId;
    private String createdAt;
    private String finishedAt;
    private List<PaperAiDecisionVO> decisions;
}
