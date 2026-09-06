package com.mychat.service.agent.pipeline;

import reactor.core.publisher.Mono;

/**
 * 主聊天回合中的一个可插拔阶段，按 {@link ChatTurnPipeline} 注册顺序串行执行。
 * <p>
 * 怎么读：成功路径 Stage 只做「调用既有服务 + 推 NDJSON」；Memory / done / turns 不在此接口，
 * 由 {@link ChatTurnFinalizer} 在 {@code doFinally} 按信号收尾，避免 cancel 时语义变化。
 */
public interface ChatTurnStage {

    /**
     * 阶段短名，用于日志与单测核对注册顺序。
     */
    String name();

    /**
     * 执行本阶段。通过 {@code ctx} 读写 sink / 编排结果；可空完成表示跳过。
     */
    Mono<Void> execute(ChatTurnContext ctx);
}
