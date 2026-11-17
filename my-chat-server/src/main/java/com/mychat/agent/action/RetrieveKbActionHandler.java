package com.mychat.agent.action;

import com.mychat.agent.api.AgentAction;
import com.mychat.agent.api.AgentRunRequest;
import com.mychat.agent.api.AgentStepResult;
import com.mychat.agent.runtime.AgentActionHandler;
import com.mychat.agent.runtime.AgentDecision;
import com.mychat.service.agent.worker.KbWorker;
import com.mychat.service.agent.worker.WorkerOutcome;
import com.mychat.service.agent.worker.WorkerPromptSupport;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/** 将现有 KbWorker 适配为知识库检索 Action，保留 scope 与 citations。 */
@Component
public class RetrieveKbActionHandler implements AgentActionHandler {

    private final KbWorker worker;

    /** 注入现有 Worker。 */
    public RetrieveKbActionHandler(KbWorker worker) {
        this.worker = worker;
    }

    /** 返回动作描述。 */
    @Override
    public AgentAction action() {
        return new AgentAction("retrieve_kb", "查询已绑定知识库",
                "{\"instruction\":\"string\",\"kbScope\":\"catalog|vector\"}",
                "text", AgentAction.SideEffectLevel.READ_ONLY);
    }

    /** 复用原检索优先级和 RAG 生成路径。 */
    @Override
    public AgentStepResult handle(AgentRunRequest request, AgentDecision decision, int stepIndex) {
        if (!StringUtils.hasText(request.context().kbId())) {
            throw new IllegalArgumentException("retrieve_kb 需要 kbId");
        }
        String scope = String.valueOf(decision.parameters().getOrDefault("kbScope", "vector"));
        String message = WorkerPromptSupport.buildWorkerUserMessage(
                request.context().metadata().get("dialogueHistory"), decision.instruction());
        WorkerOutcome outcome = worker.run(message, request.context().kbId(), request.input(),
                decision.instruction(), scope);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("kbScope", scope);
        metadata.put("citations", outcome.citations() == null ? java.util.List.of() : outcome.citations());
        metadata.put("toolCalls", 1);
        metadata.put("toolName", "knowledge-retrieval");
        return new AgentStepResult(stepIndex, action().id(), AgentStepResult.Status.SUCCEEDED,
                outcome.observation(), null, metadata);
    }
}
