package com.mychat.service.agent.pipeline.stage;

import com.mychat.entity.dto.OrchestrateRequest;
import com.mychat.service.agent.AgentOrchestratorService;
import com.mychat.service.agent.pipeline.ChatTurnContext;
import com.mychat.service.agent.pipeline.ChatTurnEmitter;
import com.mychat.service.agent.pipeline.ChatTurnStage;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 调用编排循环，逐步把 Worker/finish 推成 NDJSON step。
 * <p>
 * 怎么读：本阶段只组 {@link OrchestrateRequest} 并回调 {@link ChatTurnEmitter#emitOrchestrateStep}；
 * 决策与 Worker 仍在 {@link AgentOrchestratorService}。
 */
@Component
public class OrchestrateStage implements ChatTurnStage {

    private final AgentOrchestratorService agentOrchestratorService;

    public OrchestrateStage(AgentOrchestratorService agentOrchestratorService) {
        this.agentOrchestratorService = agentOrchestratorService;
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
        return Mono.fromCallable(() -> {
                    OrchestrateRequest request = new OrchestrateRequest();
                    request.setInput(ctx.getAgentInput());
                    request.setKbId(ctx.getKbId());
                    request.setWorkDir(ctx.getWorkDir());
                    request.setDialogueHistory(ctx.getDialogueHistory());
                    return agentOrchestratorService.orchestrate(
                            request, step -> ChatTurnEmitter.emitOrchestrateStep(ctx, step));
                })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnNext(ctx::setOrchestrateResult)
                .then();
    }
}
