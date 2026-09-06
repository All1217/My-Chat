package com.mychat.service.agent.pipeline;

import com.mychat.common.ChatStreamEvent;
import com.mychat.service.chat.ChatAssistantTurnService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import reactor.core.scheduler.Schedulers;

import java.util.List;

/**
 * 回合收尾：按 Reactor 信号写 Memory / 发 done / 异步落 turns。
 * <p>
 * 怎么读：由 {@link ChatTurnPipeline} 在 {@code doFinally} 调用，不是 {@link ChatTurnStage}。
 * Memory 与 done 仅 {@code ON_COMPLETE}；{@code chat_assistant_turns} 在 complete/cancel/error 都写。
 */
@Slf4j
@Component
public class ChatTurnFinalizer {

    private final OrchestrateTurnPersistence persistence;
    private final ChatAssistantTurnService chatAssistantTurnService;

    public ChatTurnFinalizer(
            OrchestrateTurnPersistence persistence,
            ChatAssistantTurnService chatAssistantTurnService) {
        this.persistence = persistence;
        this.chatAssistantTurnService = chatAssistantTurnService;
    }

    /**
     * 按信号收尾本回合。
     *
     * @param ctx    本回合状态
     * @param signal 管道终止信号
     */
    public void finalizeTurn(ChatTurnContext ctx, SignalType signal) {
        // 读路径：dialogueHistory 注入决策/Worker/最终流式；Worker 用 orch-*，不写 chatId。
        // 写路径：回合结束显式落库短 USER+ASSISTANT（不含附件正文）。
        if (signal == SignalType.ON_COMPLETE) {
            persistence.persist(ctx);
        }
        completeSink(ctx, signal);
    }

    /**
     * 成功则发 done、关闭 sink，并异步保存 UI 轨迹。
     */
    private void completeSink(ChatTurnContext ctx, SignalType signal) {
        if (signal == SignalType.ON_COMPLETE) {
            ChatTurnEmitter.emitDone(ctx);
        }
        ctx.getSink().tryEmitComplete();

        boolean cancelledOrError = signal == SignalType.CANCEL || signal == SignalType.ON_ERROR;
        List<ChatStreamEvent> snapshot = List.copyOf(ctx.getAccumulated());
        String turnId = ctx.getTurnId();
        Mono.fromRunnable(() -> chatAssistantTurnService.saveTurnFromEvents(
                        ctx.getChatId(), turnId, snapshot, cancelledOrError))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        null,
                        err -> log.error("异步保存助手回合失败 turnId={}", turnId, err)
                );
    }
}
