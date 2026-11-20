package com.mychat.agent.trace;

import com.mychat.agent.api.AgentRunRequest;
import com.mychat.agent.api.AgentStepResult;
import com.mychat.agent.api.PublicProgressEvent;
import com.mychat.agent.persistence.AgentRunRepository;
import com.mychat.agent.runtime.AgentDecision;
import com.mychat.agent.runtime.AgentRunListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/** 将 Runtime 生命周期转换为主聊天可见步骤。 */
@Component
public class AgentPublicRunListener implements AgentRunListener {

    private final AgentProgressHub hub;

    /** 注入公开进度路由器。 */
    public AgentPublicRunListener(AgentProgressHub hub) {
        this.hub = hub;
    }

    /** 发布动作开始，前端按稳定 stepId 原位更新。 */
    @Override
    public void onStepStarted(AgentRunRequest request, AgentDecision decision, int stepIndex) {
        hub.publish(new PublicProgressEvent(
                request.context().runId(),
                AgentRunRepository.stepId(request.context().runId(), stepIndex),
                null,
                PublicProgressEvent.Type.STEP,
                AgentStepResult.Status.RUNNING,
                displayName(decision.actionId()),
                "",
                0,
                Map.of("actionId", decision.actionId(), "stepIndex", stepIndex)));
    }

    /** 发布动作完成摘要、citation 与耗时占位。 */
    @Override
    public void onStepCompleted(AgentRunRequest request, AgentStepResult result) {
        Map<String, Object> details = new java.util.LinkedHashMap<>();
        details.put("actionId", result.actionId());
        details.put("stepIndex", result.index());
        copy(details, result.metadata(), "citations");
        copy(details, result.metadata(), "kbScope");
        hub.publish(new PublicProgressEvent(
                request.context().runId(),
                AgentRunRepository.stepId(request.context().runId(), result.index()),
                null,
                result.status() == AgentStepResult.Status.FAILED
                        ? PublicProgressEvent.Type.ERROR : PublicProgressEvent.Type.STEP,
                result.status(),
                displayName(result.actionId()),
                result.status() == AgentStepResult.Status.FAILED
                        ? result.errorMessage() : result.observation(),
                0,
                details));
    }

    /** 复制允许公开的步骤元数据。 */
    private void copy(Map<String, Object> target, Map<String, Object> source, String key) {
        if (source.get(key) != null) {
            target.put(key, source.get(key));
        }
    }

    /** 映射用户可见动作名称。 */
    private String displayName(String actionId) {
        return switch (actionId) {
            case "retrieve_kb" -> "知识库检索";
            case "file" -> "文件处理";
            case "shell" -> "命令行";
            case "search" -> "联网搜索";
            case "general" -> "分析与生成";
            default -> actionId;
        };
    }

}
