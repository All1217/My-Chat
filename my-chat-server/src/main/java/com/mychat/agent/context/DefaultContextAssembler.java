package com.mychat.agent.context;

import com.mychat.agent.api.AgentRunContext;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/** 默认上下文装配器：按优先级分配预算，再按原始顺序输出。 */
@Component
public class DefaultContextAssembler implements ContextAssembler {

    /** 按总预算和槽位预算装配上下文。 */
    @Override
    public ContextBundle assemble(
            AgentRunContext runContext,
            List<ContextBlock> blocks,
            ContextBudgetPolicy policy) {
        if (runContext == null || policy == null) {
            throw new IllegalArgumentException("运行上下文和预算策略不能为空");
        }
        List<IndexedBlock> indexed = normalize(blocks);
        String[] retained = new String[indexed.size()];
        ContextBudgetReport.Entry[] reports = new ContextBudgetReport.Entry[indexed.size()];
        int remaining = policy.maxTotalChars();

        // 1. 高优先级先拿预算，同优先级保持声明顺序
        List<IndexedBlock> allocationOrder = indexed.stream()
                .sorted(Comparator.comparingInt((IndexedBlock b) -> b.block().priority())
                        .reversed()
                        .thenComparingInt(IndexedBlock::index))
                .toList();
        for (IndexedBlock item : allocationOrder) {
            ContextBlock block = item.block();
            int original = block.content().length();
            int blockLimit = Math.min(original, policy.limitFor(block));
            int keep = Math.min(blockLimit, remaining);
            retained[item.index()] = block.content().substring(0, keep);
            remaining -= keep;
            reports[item.index()] = report(block.slot(), original, keep, blockLimit, remaining);
        }

        // 2. 还原声明顺序，空预算块只保留在报告中
        List<ContextBlock> output = new ArrayList<>();
        int originalTotal = 0;
        int retainedTotal = 0;
        for (IndexedBlock item : indexed) {
            String text = retained[item.index()];
            originalTotal += item.block().content().length();
            retainedTotal += text.length();
            if (!text.isEmpty()) {
                output.add(item.block().withContent(text));
            }
        }
        ContextBudgetReport report = new ContextBudgetReport(
                originalTotal, retainedTotal, List.of(reports));
        return new ContextBundle(output, report, fingerprint(runContext, output));
    }

    /** 清理空块并统一换行，确保 fingerprint 跨平台稳定。 */
    private static List<IndexedBlock> normalize(List<ContextBlock> blocks) {
        List<IndexedBlock> out = new ArrayList<>();
        if (blocks == null) {
            return out;
        }
        for (ContextBlock block : blocks) {
            if (block == null || block.content().isBlank()) {
                continue;
            }
            String normalized = block.content().replace("\r\n", "\n").replace('\r', '\n').strip();
            out.add(new IndexedBlock(out.size(), block.withContent(normalized)));
        }
        return out;
    }

    /** 生成单块预算报告。 */
    private static ContextBudgetReport.Entry report(
            String slot, int original, int retained, int blockLimit, int remainingAfter) {
        boolean truncated = retained < original;
        String reason = "";
        if (truncated) {
            reason = retained < blockLimit || remainingAfter == 0 ? "TOTAL_BUDGET" : "SLOT_LIMIT";
        }
        return new ContextBudgetReport.Entry(slot, original, retained, truncated, reason);
    }

    /** 只对装配后的语义内容做 SHA-256，不把正文写日志。 */
    private static String fingerprint(AgentRunContext context, List<ContextBlock> blocks) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, context.scenarioId().name());
            update(digest, context.kbId());
            update(digest, context.workDir());
            update(digest, context.businessRef());
            for (ContextBlock block : blocks) {
                update(digest, block.slot());
                update(digest, block.role().name());
                update(digest, block.source().name());
                update(digest, String.valueOf(block.priority()));
                update(digest, block.content());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) {
            throw new IllegalStateException("生成上下文 fingerprint 失败", e);
        }
    }

    /** 写入带边界的 fingerprint 字段。 */
    private static void update(MessageDigest digest, String value) {
        byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
        digest.update((byte) 0);
        digest.update(bytes);
    }

    /** 保留声明序号的内部上下文块。 */
    private record IndexedBlock(int index, ContextBlock block) {
    }
}
