package com.mychat.apps.papertrade.audit;

import com.mychat.agent.api.AgentScenarioId;

/** Agent 工具链 ThreadLocal：仅在 ProfileToolCapability 执行期间有值。 */
public final class AgentToolAuditContext {

    private static final ThreadLocal<Holder> CTX = new ThreadLocal<>();

    private AgentToolAuditContext() {
    }

    /** 进入 Agent 工具执行区间。 */
    public static void enter(AgentScenarioId scenarioId, String agentRunId) {
        CTX.set(new Holder(
                scenarioId == null ? null : scenarioId.name(),
                agentRunId));
    }

    /** 离开区间并清理。 */
    public static void clear() {
        CTX.remove();
    }

    /** 当前是否在 Agent 工具链中。 */
    public static boolean active() {
        return CTX.get() != null;
    }

    public static String scenarioId() {
        Holder h = CTX.get();
        return h == null ? null : h.scenarioId();
    }

    public static String agentRunId() {
        Holder h = CTX.get();
        return h == null ? null : h.agentRunId();
    }

    private record Holder(String scenarioId, String agentRunId) {
    }
}
