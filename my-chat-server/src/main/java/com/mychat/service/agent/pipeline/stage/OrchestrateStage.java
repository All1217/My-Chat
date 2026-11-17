package com.mychat.service.agent.pipeline.stage;

import com.mychat.service.agent.MainChatRuntimeService;
import com.mychat.service.agent.pipeline.ChatTurnContext;
import com.mychat.service.agent.pipeline.ChatTurnStage;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 调用正式 MAIN_CHAT Runtime，并把结果写入回合上下文。
 */
@Component
public class OrchestrateStage implements ChatTurnStage {

    private final MainChatRuntimeService mainChatRuntimeService;

    /** 注入正式主聊天 Runtime。 */
    public OrchestrateStage(MainChatRuntimeService mainChatRuntimeService) {
        this.mainChatRuntimeService = mainChatRuntimeService;
    }

    /**
     * 阶段短名。
     */
    @Override
    public String name() {
        return "orchestrate";
    }

    /**
     * 在 boundedElastic 上跑同步编排循环，结果写入 ctx。
     */
    @Override
    public Mono<Void> execute(ChatTurnContext ctx) {
        return Mono.fromCallable(() -> mainChatRuntimeService.run(ctx))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnNext(result -> {
                    ctx.setAgentRunResult(result);
                    if (result.status() != com.mychat.agent.api.AgentRunResult.Status.SUCCEEDED) {
                        throw new IllegalStateException(
                                "MAIN_CHAT Runtime 结束状态: " + result.status()
                                        + " " + result.errorMessage());
                    }
                })
                .then();
    }
}
