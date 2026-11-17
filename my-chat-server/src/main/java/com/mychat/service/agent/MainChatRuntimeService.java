package com.mychat.service.agent;

import com.mychat.agent.api.AgentRunRequest;
import com.mychat.agent.api.AgentRunResult;
import com.mychat.agent.runtime.AgentRuntime;
import com.mychat.agent.trace.AgentProgressHub;
import com.mychat.common.ChatStreamEvent;
import com.mychat.service.agent.pipeline.ChatTurnContext;
import com.mychat.service.agent.pipeline.ChatTurnEmitter;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/** 正式主聊天到通用 AgentRuntime 的适配服务。 */
@Service
public class MainChatRuntimeService {

    private final AgentRuntime runtime;
    private final AgentProgressHub progressHub;

    /** 注入 Runtime 和实时公开进度桥。 */
    public MainChatRuntimeService(AgentRuntime runtime, AgentProgressHub progressHub) {
        this.runtime = runtime;
        this.progressHub = progressHub;
    }

    /** 运行 MAIN_CHAT，并同步推送公开步骤事件。 */
    public AgentRunResult run(ChatTurnContext ctx) {
        if (ctx.getRunContext() == null || ctx.getContextBundle() == null) {
            throw new IllegalStateException("MAIN_CHAT ContextBundle 尚未装配");
        }
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("contextText", ctx.getRenderedContext());
        parameters.put("contextFingerprint", ctx.getContextBundle().fingerprint());
        parameters.put("maxDurationMs", 180_000L);
        parameters.put("maxTokens", 16_000L);
        AgentRunRequest request = new AgentRunRequest(
                ctx.getOriginalPrompt(), ctx.getRunContext(), parameters);
        try (AutoCloseable ignored = progressHub.register(
                ctx.getRunContext().runId(),
                event -> ChatTurnEmitter.emit(
                        ctx, ChatStreamEvent.publicProgress(ctx.getTurnId(), ctx.getSeq(), event)))) {
            return runtime.run(request);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("关闭 Agent 进度桥失败", exception);
        }
    }

}
