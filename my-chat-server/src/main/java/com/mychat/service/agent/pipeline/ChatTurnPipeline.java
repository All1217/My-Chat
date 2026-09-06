package com.mychat.service.agent.pipeline;

import com.mychat.service.agent.pipeline.stage.EmitRouteStage;
import com.mychat.service.agent.pipeline.stage.LoadDialogueContextStage;
import com.mychat.service.agent.pipeline.stage.OrchestrateStage;
import com.mychat.service.agent.pipeline.stage.QualityLoopStage;
import com.mychat.service.agent.pipeline.stage.StreamFinalAnswerStage;
import com.mychat.utils.ChatStreamEventWriter;
import com.mychat.utils.NdjsonStreamSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 主聊天 NDJSON 管道：按显式顺序串行执行 Stage，收尾交给 Finalizer。
 * <p>
 * 怎么读：上接 {@link com.mychat.service.agent.ChatOrchestrateStreamService}，
 * 成功路径见构造器里的阶段列表；加长流程 = 新 {@link ChatTurnStage} + 本列表插一行。
 * 不要把 Memory/done 做成普通 Stage。
 */
@Slf4j
@Service
public class ChatTurnPipeline {

    private final List<ChatTurnStage> stages;
    private final ChatTurnFinalizer finalizer;
    private final ChatStreamEventWriter eventWriter;

    public ChatTurnPipeline(
            EmitRouteStage emitRouteStage,
            LoadDialogueContextStage loadDialogueContextStage,
            OrchestrateStage orchestrateStage,
            StreamFinalAnswerStage streamFinalAnswerStage,
            QualityLoopStage qualityLoopStage,
            ChatTurnFinalizer finalizer,
            ChatStreamEventWriter eventWriter) {
        // 注册顺序即执行顺序；比 @Order 扫描更可读
        this.stages = List.of(
                emitRouteStage,
                loadDialogueContextStage,
                orchestrateStage,
                streamFinalAnswerStage,
                qualityLoopStage);
        this.finalizer = finalizer;
        this.eventWriter = eventWriter;
    }

    /**
     * 主路：route → 读上下文 → 编排 → text_delta → 可选质量环 → Finalizer 落库。
     *
     * @param agentInput     含附件正文的编排输入
     * @param memoryUserText 写入 spring_ai_chat_memory 的短 USER（仅文件名+原问）
     * @param originalPrompt 用户原问（质量环 goal 等）
     */
    public Flux<String> streamOrchestrate(
            String agentInput,
            String memoryUserText,
            String originalPrompt,
            String chatId,
            String kbId,
            boolean qualityLoop,
            String criteria) {
        ChatTurnContext ctx = ChatTurnContext.open(
                agentInput, memoryUserText, originalPrompt, chatId, kbId, qualityLoop, criteria);
        Mono<Void> drive = Flux.fromIterable(stages)
                .concatMap(stage -> stage.execute(ctx)
                        .doOnSubscribe(s -> log.debug(
                                "主聊天阶段开始 name={} turnId={}", stage.name(), ctx.getTurnId())))
                .then()
                .doOnError(e -> ChatTurnEmitter.emitError(ctx, e))
                .doFinally(signal -> finalizer.finalizeTurn(ctx, signal))
                .then();
        return NdjsonStreamSupport.mergeNdjson(ctx.getSink(), drive, eventWriter);
    }

    /**
     * 成功路径阶段短名，便于单测核对注册顺序。
     */
    List<String> stageNames() {
        return stages.stream().map(ChatTurnStage::name).toList();
    }
}
