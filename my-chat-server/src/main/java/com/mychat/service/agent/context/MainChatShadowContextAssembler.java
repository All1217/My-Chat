package com.mychat.service.agent.context;

import com.mychat.agent.api.AgentRunContext;
import com.mychat.agent.context.ContextAssembler;
import com.mychat.agent.context.ContextBlock;
import com.mychat.agent.context.ContextBudgetPolicy;
import com.mychat.agent.context.ContextBundle;
import com.mychat.service.agent.pipeline.ChatTurnContext;
import com.mychat.vo.OrchestrateStepVO;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/** 主聊天 shadow 适配器：镜像现有输入，但不改变真实 Prompt。 */
@Component
public class MainChatShadowContextAssembler {

    private static final String SUMMARY_HEADER = "【会话摘要｜较早轮次压缩，可能有损】";
    private static final String RECENT_HEADER = "【近期对话原文】";
    private static final String USER_QUESTION_MARKER = "用户的问题：";

    private final ContextAssembler assembler;

    public MainChatShadowContextAssembler(ContextAssembler assembler) {
        this.assembler = assembler;
    }

    /** 从现有回合状态构建显式 RunContext 与 shadow ContextBundle。 */
    public ShadowAssembly assemble(ChatTurnContext turn) {
        AgentRunContext runContext = AgentRunContext.mainChatShadow(
                turn.getTurnId(), turn.getChatId(), turn.getKbId(), turn.getWorkDir());
        List<ContextBlock> blocks = new ArrayList<>();
        add(blocks, "user.goal", 100, 8_000, turn.getOriginalPrompt(), ContextBlock.Source.READ_ONLY);
        add(blocks, "upload.turn", 95, 16_000, extractUpload(turn), ContextBlock.Source.EPHEMERAL_UPLOAD);
        addDialogue(blocks, turn.getDialogueHistory());
        add(blocks, "workspace.dir", 40, 500, turn.getWorkDir(), ContextBlock.Source.READ_ONLY);
        addSteps(blocks, turn);
        ContextBundle bundle = assembler.assemble(
                runContext, blocks, ContextBudgetPolicy.mainChatShadow());
        return new ShadowAssembly(runContext, bundle);
    }

    /** 把对话固定格式拆成摘要与近期原文两个独立槽位。 */
    private static void addDialogue(List<ContextBlock> blocks, String dialogue) {
        if (!StringUtils.hasText(dialogue)) {
            return;
        }
        int summaryStart = dialogue.indexOf(SUMMARY_HEADER);
        int recentStart = dialogue.indexOf(RECENT_HEADER);
        if (summaryStart >= 0 && recentStart > summaryStart) {
            String summary = dialogue.substring(summaryStart + SUMMARY_HEADER.length(), recentStart);
            String recent = dialogue.substring(recentStart + RECENT_HEADER.length());
            add(blocks, "memory.summary", 80, 1_800, withoutPlaceholder(summary),
                    ContextBlock.Source.DERIVED);
            add(blocks, "memory.recent", 90, 4_000, withoutPlaceholder(recent),
                    ContextBlock.Source.READ_ONLY);
            return;
        }
        add(blocks, "memory.recent", 90, 4_000, dialogue, ContextBlock.Source.READ_ONLY);
    }

    /** 将编排观察镜像成知识库或通用工具上下文。 */
    private static void addSteps(List<ContextBlock> blocks, ChatTurnContext turn) {
        if (turn.getOrchestrateResult() == null || turn.getOrchestrateResult().getSteps() == null) {
            return;
        }
        for (OrchestrateStepVO step : turn.getOrchestrateResult().getSteps()) {
            if (step == null || !StringUtils.hasText(step.getObservation())) {
                continue;
            }
            String slot = "retrieve_kb".equals(step.getAction())
                    ? "knowledge.context"
                    : "tool.observations";
            int priority = "knowledge.context".equals(slot) ? 85 : 70;
            add(blocks, slot, priority, 8_000, step.getObservation(),
                    ContextBlock.Source.TOOL_OBSERVATION);
        }
    }

    /** 提取附件前缀；无附件时 agentInput 与原问一致，不产生块。 */
    private static String extractUpload(ChatTurnContext turn) {
        String agentInput = turn.getAgentInput();
        if (!StringUtils.hasText(agentInput)
                || agentInput.equals(turn.getOriginalPrompt())) {
            return null;
        }
        int marker = agentInput.lastIndexOf(USER_QUESTION_MARKER);
        return marker > 0 ? agentInput.substring(0, marker).trim() : agentInput;
    }

    /** 占位“（无）”不进入模型上下文块。 */
    private static String withoutPlaceholder(String text) {
        String value = text == null ? "" : text.trim();
        return "（无）".equals(value) ? null : value;
    }

    /** 非空时追加用户上下文块。 */
    private static void add(
            List<ContextBlock> blocks,
            String slot,
            int priority,
            int maxChars,
            String content,
            ContextBlock.Source source) {
        if (StringUtils.hasText(content)) {
            blocks.add(new ContextBlock(
                    slot, ContextBlock.Role.USER, source, priority, maxChars, content));
        }
    }

    /** shadow 结果：显式运行上下文 + 不参与生产 Prompt 的上下文快照。 */
    public record ShadowAssembly(AgentRunContext runContext, ContextBundle bundle) {
    }
}
