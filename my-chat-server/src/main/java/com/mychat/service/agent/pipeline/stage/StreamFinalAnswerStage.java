package com.mychat.service.agent.pipeline.stage;

import com.mychat.service.agent.pipeline.ChatTurnContext;
import com.mychat.service.agent.pipeline.ChatTurnEmitter;
import com.mychat.service.agent.pipeline.ChatTurnStage;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

/** 把 Runtime AnswerPolicy 结果推送为主聊天 text_delta。 */
@Component
public class StreamFinalAnswerStage implements ChatTurnStage {

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
        if (ctx.getAgentRunResult() != null) {
            String output = ctx.getAgentRunResult().output();
            if (StringUtils.hasText(output)) {
                ChatTurnEmitter.emitTextDelta(ctx, output);
            }
        }
        return Mono.empty();
    }
}
