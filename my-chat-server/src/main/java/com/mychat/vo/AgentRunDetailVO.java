package com.mychat.vo;

import com.mychat.agent.api.PublicProgressEvent;

import java.util.List;

/** Agent Run 安全详情：公开时间线和审批，不返回 checkpoint。 */
public record AgentRunDetailVO(
        AgentRunSummaryVO run,
        List<PublicProgressEvent> timeline,
        List<AgentApprovalVO> approvals) {

    /** 固化详情集合。 */
    public AgentRunDetailVO {
        timeline = timeline == null ? List.of() : List.copyOf(timeline);
        approvals = approvals == null ? List.of() : List.copyOf(approvals);
    }
}
