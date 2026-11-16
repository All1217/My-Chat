package com.mychat.agent.context;

/** 可预算、可溯源的上下文块。 */
public record ContextBlock(
        String slot,
        Role role,
        Source source,
        int priority,
        int maxChars,
        String content) {

    /** Prompt 中的块角色。 */
    public enum Role {
        SYSTEM,
        USER
    }

    /** 上下文来源类别。 */
    public enum Source {
        READ_ONLY,
        DERIVED,
        EPHEMERAL_UPLOAD,
        TOOL_OBSERVATION
    }

    /** 校验上下文块；priority 越大越优先保留。 */
    public ContextBlock {
        if (slot == null || slot.isBlank()) {
            throw new IllegalArgumentException("ContextBlock.slot 不能为空");
        }
        if (role == null || source == null) {
            throw new IllegalArgumentException("ContextBlock.role/source 不能为空");
        }
        if (priority < 0 || maxChars < 1) {
            throw new IllegalArgumentException("ContextBlock 预算非法");
        }
        content = content == null ? "" : content;
    }

    /** 返回裁剪后的同源上下文块。 */
    public ContextBlock withContent(String retained) {
        return new ContextBlock(slot, role, source, priority, maxChars, retained);
    }
}
