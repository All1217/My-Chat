package com.mychat.agent.api;

/** 通用 Agent 场景标识；场景通过 Profile 注册，不进入运行时 switch。 */
public enum AgentScenarioId {
    MAIN_CHAT,
    PAPER_RESEARCH,
    PAPER_TRADER,
    PAPER_WAREHOUSE_ANALYST,
    PAPER_TRADE_EVALUATOR,
    MARKET_ANALYSIS,
    ROLEPLAY,
    INTERVIEWER
}
