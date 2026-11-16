package com.mychat.service.agent.pipeline.stage;

import com.mychat.service.agent.context.MainChatShadowContextAssembler;
import com.mychat.service.agent.pipeline.ChatTurnContext;
import com.mychat.service.agent.pipeline.ChatTurnStage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/** Phase 1 shadow 阶段：装配新上下文并记录诊断，不改变模型输入或 NDJSON。 */
@Slf4j
@Component
public class ShadowAssembleContextStage implements ChatTurnStage {

    private final MainChatShadowContextAssembler assembler;

    public ShadowAssembleContextStage(MainChatShadowContextAssembler assembler) {
        this.assembler = assembler;
    }

    /** shadow 阶段短名。 */
    @Override
    public String name() {
        return "assemble_context_shadow";
    }

    /** 构建 shadow 快照；日志只写 fingerprint 与长度，不写正文。 */
    @Override
    public Mono<Void> execute(ChatTurnContext ctx) {
        return Mono.fromRunnable(() -> {
            MainChatShadowContextAssembler.ShadowAssembly shadow = assembler.assemble(ctx);
            ctx.setShadowRunContext(shadow.runContext());
            ctx.setShadowContextBundle(shadow.bundle());
            log.debug(
                    "上下文 shadow 完成 turnId={} fingerprint={} budget={}",
                    ctx.getTurnId(),
                    shadow.bundle().fingerprint(),
                    shadow.bundle().budgetReport().totalsOnly());
        });
    }
}
