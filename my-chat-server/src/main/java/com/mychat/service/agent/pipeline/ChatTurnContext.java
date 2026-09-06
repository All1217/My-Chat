package com.mychat.service.agent.pipeline;

import com.mychat.common.ChatStreamEvent;
import com.mychat.config.WorkspaceContext;
import com.mychat.vo.OrchestrateResultVO;
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
public class ChatTurnContext {

    /** 主聊天固定 route 名（与旧 NDJSON 一致）。 */
    public static final String AGENT_MODE_ORCHESTRATE = "orchestrate";

    private final String agentInput;
    private final String memoryUserText;
    private final String originalPrompt;
    private final String chatId;
    private final String kbId;
    private final boolean qualityLoop;
    private final String criteria;

    private final String turnId;
    private final AtomicInteger seq;
    private final Sinks.Many<ChatStreamEvent> sink;
    private final List<ChatStreamEvent> accumulated;

    /** 开回合时在调用线程捕获，避免后续 Stage 换线程后读不到 ThreadLocal。 */
    private final String workDir;

    private volatile String dialogueHistory;
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

    /** 含附件正文的编排输入。 */
    public String getAgentInput() {
        return agentInput;
    }

    /** 写入 Memory 的短 USER（文件名+原问）。 */
    public String getMemoryUserText() {
        return memoryUserText;
    }

    /** 用户原问（质量环 goal、最终答复 userGoal）。 */
    public String getOriginalPrompt() {
        return originalPrompt;
    }

    /** 会话 ID。 */
    public String getChatId() {
        return chatId;
    }

    /** 本回合知识库；可空。 */
    public String getKbId() {
        return kbId;
    }

    /** 是否尝试写盘质量环。 */
    public boolean isQualityLoop() {
        return qualityLoop;
    }

    /** 质量环评价标准；可空则用服务端默认文案。 */
    public String getCriteria() {
        return criteria;
    }

    /** 本回合流式 ID（chatId-UUID）。 */
    public String getTurnId() {
        return turnId;
    }

    /** NDJSON 序号生成器。 */
    public AtomicInteger getSeq() {
        return seq;
    }

    /** 边生成边推前端的直播通道。 */
    public Sinks.Many<ChatStreamEvent> getSink() {
        return sink;
    }

    /** 本回合事件清单，结束后用来落库/回放。 */
    public List<ChatStreamEvent> getAccumulated() {
        return accumulated;
    }

    /** 开回合时捕获的工作目录；可空。 */
    public String getWorkDir() {
        return workDir;
    }

    /** 滚动摘要 + 近期原文；无历史时为 null。 */
    public String getDialogueHistory() {
        return dialogueHistory;
    }

    /** 由读上下文阶段写入。 */
    public void setDialogueHistory(String dialogueHistory) {
        this.dialogueHistory = dialogueHistory;
    }

    /** 编排循环结果；未跑完时为 null。 */
    public OrchestrateResultVO getOrchestrateResult() {
        return orchestrateResult;
    }

    /** 由编排阶段写入。 */
    public void setOrchestrateResult(OrchestrateResultVO orchestrateResult) {
        this.orchestrateResult = orchestrateResult;
    }
}
