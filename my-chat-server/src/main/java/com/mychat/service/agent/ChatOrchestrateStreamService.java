package com.mychat.service.agent;

import com.mychat.service.agent.pipeline.ChatTurnPipeline;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * 主聊天 NDJSON 管道门面：对外签名不变，内部委托 {@link ChatTurnPipeline}。
 * <p>
 * 怎么读：上接 {@code ChatController}，下接 {@link ChatTurnPipeline}（Stage 有序列表）。
 * 读上下文 / 编排 / 质量环 / 落库分别在各 Stage 与 Finalizer；Worker 仍用 {@code orch-*}。
 * 附件拼装在入口由 {@link ChatUploadEnrichment} 完成，本类只收已经分好的 agentInput / memoryUserText。
 */
@Service
public class ChatOrchestrateStreamService {

    private final ChatTurnPipeline chatTurnPipeline;

    public ChatOrchestrateStreamService(ChatTurnPipeline chatTurnPipeline) {
        this.chatTurnPipeline = chatTurnPipeline;
    }

    /**
     * 主路：route=orchestrate → step → text_delta → 可选质量环 → Memory/turn 落库。
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
        return chatTurnPipeline.streamOrchestrate(
                agentInput, memoryUserText, originalPrompt, chatId, kbId, qualityLoop, criteria);
    }
}
