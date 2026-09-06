package com.mychat.service.agent.pipeline.stage;

import com.mychat.service.agent.pipeline.ChatTurnContext;
import com.mychat.service.agent.pipeline.ChatTurnEmitter;
import com.mychat.service.agent.pipeline.ChatTurnStage;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 成功路径第一阶段：推 NDJSON route=orchestrate。
 * <p>
 * 怎么读：上接 {@link com.mychat.service.agent.pipeline.ChatTurnPipeline}，
 * 下接 {@link LoadDialogueContextStage}。
 */
@Component
public class EmitRouteStage implements ChatTurnStage {

    /**
     * 阶段短名。
     */
    @Override
    public String name() {
        return "emit_route";
    }

    /**
     * 同步推首包 route，与旧管道开场事件一致。
     */
    @Override
    public Mono<Void> execute(ChatTurnContext ctx) {
        return Mono.fromRunnable(() -> ChatTurnEmitter.emitRoute(ctx));
    }
}
