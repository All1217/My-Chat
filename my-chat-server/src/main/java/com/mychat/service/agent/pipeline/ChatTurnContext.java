package com.mychat.service.agent.pipeline;

import com.mychat.common.ChatStreamEvent;
import com.mychat.config.WorkspaceContext;
import com.mychat.vo.OrchestrateResultVO;
import lombok.Getter;
import lombok.Setter;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 主聊天单回合共享状态：请求入参不可变，流会话与阶段产物可变。
 * <p>
 * 怎么读：{@link ChatTurnPipeline} 开回合时 {@link #open}，各 {@link ChatTurnStage} 读写本对象，
 * {@link ChatTurnFinalizer} 在 {@code doFinally} 里按 signal 落库。
 */
@Getter
public class ChatTurnContext {

    /** 主聊天固定 route 名（与旧 NDJSON 一致）。 */
    public static final String AGENT_MODE_ORCHESTRATE = "orchestrate";

    /**
     * -- GETTER --
     * 含附件正文的编排输入。
     */
    private final String agentInput;
    /**
     * -- GETTER --
     * 写入 Memory 的短 USER（文件名+原问）。
     */
    private final String memoryUserText;
    /**
     * -- GETTER --
     * 用户原问（质量环 goal、最终答复 userGoal）。
     */
    private final String originalPrompt;
    /**
     * -- GETTER --
     * 会话 ID。
     */
    private final String chatId;
    /**
     * -- GETTER --
     * 本回合知识库；可空。
     */
    private final String kbId;
    /**
     * -- GETTER --
     * 是否尝试写盘质量环。
     */
    private final boolean qualityLoop;
    /**
     * -- GETTER --
     * 质量环评价标准；可空则用服务端默认文案。
     */
    private final String criteria;

    /**
     * -- GETTER --
     * 本回合流式 ID（chatId-UUID）。
     */
    private final String turnId;
    /**
     * -- GETTER --
     * NDJSON 序号生成器。
     */
    private final AtomicInteger seq;
    /**
     * -- GETTER --
     * 边生成边推前端的直播通道。
     */
    private final Sinks.Many<ChatStreamEvent> sink;
    /**
     * -- GETTER --
     * 本回合事件清单，结束后用来落库/回放。
     */
    private final List<ChatStreamEvent> accumulated;

    /** 开回合时在调用线程捕获，避免后续 Stage 换线程后读不到 ThreadLocal。
     * -- GETTER --
     * 开回合时捕获的工作目录；可空。
     */
    private final String workDir;

    /**
     * -- GETTER --
     * 滚动摘要 + 近期原文；无历史时为 null。
     * -- SETTER --
     * 由读上下文阶段写入。

     */
    @Setter
    private volatile String dialogueHistory;
    /**
     * -- GETTER --
     * 编排循环结果；未跑完时为 null。
     * -- SETTER --
     * 由编排阶段写入。

     */
    @Setter
    private volatile OrchestrateResultVO orchestrateResult;

    private ChatTurnContext(
            String agentInput,
            String memoryUserText,
            String originalPrompt,
            String chatId,
            String kbId,
            boolean qualityLoop,
            String criteria) {
        this.agentInput = agentInput;
        this.memoryUserText = memoryUserText;
        this.originalPrompt = originalPrompt;
        this.chatId = chatId;
        this.kbId = kbId;
        this.qualityLoop = qualityLoop;
        this.criteria = criteria;
        this.turnId = chatId + "-" + UUID.randomUUID();
        this.seq = new AtomicInteger(0);
        this.sink = Sinks.many().replay().limit(1024);
        this.accumulated = Collections.synchronizedList(new ArrayList<>());
        this.workDir = WorkspaceContext.get();
    }

    /**
     * 开启一回合：分配 turnId / replay sink / accumulated，并捕获当前工作区。
     */
    public static ChatTurnContext open(
            String agentInput,
            String memoryUserText,
            String originalPrompt,
            String chatId,
            String kbId,
            boolean qualityLoop,
            String criteria) {
        return new ChatTurnContext(
                agentInput, memoryUserText, originalPrompt, chatId, kbId, qualityLoop, criteria);
    }
}
