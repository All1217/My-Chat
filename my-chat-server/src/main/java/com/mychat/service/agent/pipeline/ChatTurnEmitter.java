package com.mychat.service.agent.pipeline;

import com.mychat.common.ChatStreamEvent;
import com.mychat.utils.NdjsonStreamSupport;
import com.mychat.vo.OrchestrateStepVO;

/**
 * 回合内 NDJSON 推送助手：统一走 accumulated + sink。
 * <p>
 * 怎么读：各 Stage / Finalizer 不要直接操作 sink，经本类保证时间线与落库共用同一序。
 */
public final class ChatTurnEmitter {

    private ChatTurnEmitter() {
    }

    /**
     * 将事件写入时间线快照并推入 sink。
     */
    public static void emit(ChatTurnContext ctx, ChatStreamEvent event) {
        NdjsonStreamSupport.emitTracked(ctx.getSink(), ctx.getAccumulated(), event);
    }

    /**
     * 推主聊天首包 route=orchestrate。
     */
    public static void emitRoute(ChatTurnContext ctx) {
        emit(ctx, ChatStreamEvent.route(
                ctx.getTurnId(),
                ctx.getSeq(),
                ChatTurnContext.AGENT_MODE_ORCHESTRATE,
                "主聊天默认多步编排（Orchestrator-Workers），跨能力 Worker 接力"));
    }

    /**
     * 把编排步推成 NDJSON step；retrieve_kb 的 citations / kbScope 写入 args。
     */
    public static void emitOrchestrateStep(ChatTurnContext ctx, OrchestrateStepVO step) {
        if (step == null) {
            return;
        }
        emit(ctx, ChatStreamEvent.step(
                ctx.getTurnId(),
                ctx.getSeq(),
                step.getIndex(),
                step.getAction(),
                step.getReasoning(),
                step.getInstruction(),
                step.getObservation(),
                step.getCitations(),
                step.getKbScope()));
    }

    /**
     * 推最终答复（或质量环摘要）文本增量。
     */
    public static void emitTextDelta(ChatTurnContext ctx, String delta) {
        emit(ctx, ChatStreamEvent.textDelta(ctx.getTurnId(), ctx.getSeq(), delta));
    }

    /**
     * 推用户可见 error 事件。
     */
    public static void emitError(ChatTurnContext ctx, Throwable e) {
        NdjsonStreamSupport.emitError(ctx.getSink(), ctx.getTurnId(), ctx.getSeq(), e, ctx.getAccumulated());
    }

    /**
     * 推成功结束的 done（仅 Finalizer 在 ON_COMPLETE 时调用）。
     */
    public static void emitDone(ChatTurnContext ctx) {
        emit(ctx, ChatStreamEvent.done(ctx.getTurnId(), ctx.getSeq()));
    }
}
