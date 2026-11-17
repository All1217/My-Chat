package com.mychat.apps.papertrade.workflow;

/** 模拟炒股领域固定步骤（搜集员 / 交易员共用命名空间）。 */
public enum PaperWorkflowStep {
    START_RUN,
    DISCOVER_CANDIDATES,
    VALIDATE_MARKET_DATA,
    AI_COLLECT_AND_ANALYZE,
    VALIDATE_CARDS,
    UPSERT_NOTES,
    PROCESS_RULE_EXITS,
    BUILD_CONTEXT,
    AI_REVIEW_AND_DECIDE,
    VALIDATE_DECISIONS,
    APPLY_RISK_GATES,
    EXECUTE_ORDERS,
    SETTLE_AND_FINISH,
    FINISH_RUN
}
