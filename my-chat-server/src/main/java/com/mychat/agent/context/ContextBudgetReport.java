package com.mychat.agent.context;

import java.util.List;

/** 上下文预算执行报告；只记录长度与裁剪原因，不记录正文。 */
public record ContextBudgetReport(
        int originalChars,
        int retainedChars,
        List<Entry> entries) {

    /** 单个槽位的预算结果。 */
    public record Entry(
            String slot,
            int originalChars,
            int retainedChars,
            boolean truncated,
            String reason) {
    }

    /** 固化条目列表。 */
    public ContextBudgetReport {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    /** 返回可安全写 debug 日志的长度摘要。 */
    public String totalsOnly() {
        return retainedChars + "/" + originalChars + " chars, blocks=" + entries.size();
    }
}
