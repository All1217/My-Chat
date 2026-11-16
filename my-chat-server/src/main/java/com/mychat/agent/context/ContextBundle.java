package com.mychat.agent.context;

import java.util.List;

/** 单次模型调用前的不可变上下文快照。 */
public record ContextBundle(
        List<ContextBlock> blocks,
        ContextBudgetReport budgetReport,
        String fingerprint) {

    /** 固化块列表并校验诊断信息。 */
    public ContextBundle {
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
        if (budgetReport == null || fingerprint == null || fingerprint.isBlank()) {
            throw new IllegalArgumentException("ContextBundle 诊断信息不能为空");
        }
    }

    /** 按槽位读取第一个保留块。 */
    public ContextBlock first(String slot) {
        return blocks.stream().filter(b -> b.slot().equals(slot)).findFirst().orElse(null);
    }
}
