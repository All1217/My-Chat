package com.mychat.apps.papertrade.entity.vo;

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
    private String createdAt;
    private String finishedAt;
    private List<PaperAiDecisionVO> decisions;
}
