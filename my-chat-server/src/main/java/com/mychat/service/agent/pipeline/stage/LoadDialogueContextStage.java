package com.mychat.service.agent.pipeline.stage;

import com.mychat.service.agent.OrchestrateDialogueContextService;
import com.mychat.service.agent.pipeline.ChatTurnContext;
import com.mychat.service.agent.pipeline.ChatTurnStage;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 读路径：滚动摘要 + 近期原文，写入 {@link ChatTurnContext#setDialogueHistory}。
 * <p>
 * 怎么读：不经 {@code MessageChatMemoryAdvisor(chatId)}；摘要惰性更新见
 * {@link OrchestrateDialogueContextService}。
 */
@Component
public class LoadDialogueContextStage implements ChatTurnStage {

    private final OrchestrateDialogueContextService dialogueContextService;

    public LoadDialogueContextStage(OrchestrateDialogueContextService dialogueContextService) {
        this.dialogueContextService = dialogueContextService;
    }

    /**
     * 阶段短名。
     */
    @Override
    public String name() {
        return "load_dialogue";
    }

    /**
     * 阻塞读 Memory/摘要表，放到 boundedElastic，避免占满订阅线程。
     */
    @Override
    public Mono<Void> execute(ChatTurnContext ctx) {
        return Mono.fromRunnable(() ->
                        ctx.setDialogueHistory(dialogueContextService.buildForOrchestrate(ctx.getChatId())))
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }
}
