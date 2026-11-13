package com.mychat.apps.papertrade.trader.entity.vo;

import lombok.Data;

/** 提交 AI 轮立刻返回。 */
@Data
public class PaperAiSubmitVO {
    private String runId;
    private String jobId;
    private String status;
}
