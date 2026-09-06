package com.mychat.service.agent.pipeline.stage;

import com.mychat.service.agent.AgentOrchestratorService;
import com.mychat.service.agent.pipeline.ChatTurnContext;
import com.mychat.service.agent.pipeline.ChatTurnEmitter;
import com.mychat.service.agent.pipeline.ChatTurnStage;
import com.mychat.vo.OrchestrateResultVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

/**
 * 编排结束后流式生成最终答复，每项推 text_delta。
 * <p>
 * 怎么读：token 来自 {@link AgentOrchestratorService#streamFinalAnswer}；
 * 无增量时回退整段 {@code finalAnswer}，与旧管道一致。
 */
@Slf4j
@Component
public class StreamFinalAnswerStage implements ChatTurnStage {

    private final AgentOrchestratorService agentOrchestratorService;

    public StreamFinalAnswerStage(AgentOrchestratorService agentOrchestratorService) {
        this.agentOrchestratorService = agentOrchestratorService;
    }

    /**
     * 阶段短名。
     */
    @Override
    public String name() {
        return "stream_final";
    }

    /**
     * 把最终答复增量推给前端；编排结果为空则跳过。
     */
    @Override
    public Mono<Void> execute(ChatTurnContext ctx) {
        OrchestrateResultVO result = ctx.getOrchestrateResult();
        if (result == null) {
            return Mono.empty();
        }
        StringBuilder streamed = new StringBuilder();
        return agentOrchestratorService
                .streamFinalAnswer(ctx.getOriginalPrompt(), result, ctx.getDialogueHistory())
                .doOnNext(delta -> {
                    streamed.append(delta);
                    ChatTurnEmitter.emitTextDelta(ctx, delta);
                })
                .then(Mono.fromRunnable(() -> {
                    if (streamed.isEmpty() && StringUtils.hasText(result.getFinalAnswer())) {
                        log.warn("streamFinalAnswer 无增量，回退整段 finalAnswer turnId={}", ctx.getTurnId());
                        ChatTurnEmitter.emitTextDelta(ctx, result.getFinalAnswer());
                    }
                }));
    }
}
