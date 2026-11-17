package com.mychat.agent.context;

import java.util.Map;

/** ContextAssembler 的总预算与槽位预算。Phase 1 使用字符近似，后续可替换为 token 计数。 */
public record ContextBudgetPolicy(
        int maxTotalChars,
        Map<String, Integer> slotLimits) {

    /** 固化预算配置。 */
    public ContextBudgetPolicy {
        if (maxTotalChars < 1) {
            throw new IllegalArgumentException("上下文总预算必须大于 0");
        }
        slotLimits = slotLimits == null ? Map.of() : Map.copyOf(slotLimits);
        if (slotLimits.values().stream().anyMatch(v -> v == null || v < 1)) {
            throw new IllegalArgumentException("上下文槽位预算必须大于 0");
        }
    }

    /** 返回槽位预算；未配置时沿用块自身上限。 */
    public int limitFor(ContextBlock block) {
        return Math.min(block.maxChars(), slotLimits.getOrDefault(block.slot(), block.maxChars()));
    }

    /** 主聊天正式上下文预算。 */
    public static ContextBudgetPolicy mainChat() {
        return new ContextBudgetPolicy(32_000, Map.of(
                "user.goal", 8_000,
                "upload.turn", 16_000,
                "memory.summary", 1_800,
                "memory.recent", 4_000,
                "knowledge.context", 8_000,
                "tool.observations", 8_000,
                "workspace.dir", 500));
    }
}
