package com.mychat.service.agent.pipeline.stage;

import com.mychat.service.agent.context.MainChatContextAssembler;
import com.mychat.service.agent.pipeline.ChatTurnContext;
import com.mychat.service.agent.pipeline.ChatTurnStage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/** 正式上下文阶段：为 MAIN_CHAT 装配唯一 ContextBundle。 */
@Slf4j
@Component
public class AssembleContextStage implements ChatTurnStage {

    private final MainChatContextAssembler assembler;

    /** 注入正式上下文装配器。 */
    public AssembleContextStage(MainChatContextAssembler assembler) {
        this.assembler = assembler;
    }

    /** 阶段短名。 */
    @Override
    public String name() {
        return "assemble_context";
    }

    /** 装配并保存正式 ContextBundle。 */
    @Override
    public Mono<Void> execute(ChatTurnContext ctx) {
        return Mono.fromRunnable(() -> {
            MainChatContextAssembler.Assembly assembly = assembler.assemble(ctx);
            ctx.setRunContext(assembly.runContext());
            ctx.setContextBundle(assembly.bundle());
            ctx.setRenderedContext(assembly.renderedPrompt());
            log.debug("MAIN_CHAT ContextBundle turnId={} fingerprint={} budget={}",
                    ctx.getTurnId(), assembly.bundle().fingerprint(),
                    assembly.bundle().budgetReport().totalsOnly());
        });
    }
}
