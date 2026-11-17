package com.mychat.agent.action;

import com.mychat.agent.api.AgentAction;
import com.mychat.agent.api.AgentRunRequest;
import com.mychat.agent.api.AgentStepResult;
import com.mychat.agent.runtime.AgentActionHandler;
import com.mychat.agent.runtime.AgentDecision;
import com.mychat.service.agent.worker.SearchWorker;
import com.mychat.service.agent.worker.WorkerPromptSupport;
import org.springframework.stereotype.Component;

import java.util.Map;

/** 将现有 SearchWorker 适配为联网查询 Action。 */
@Component
public class SearchActionHandler implements AgentActionHandler {

    private final SearchWorker worker;

    /** 注入现有 Worker。 */
    public SearchActionHandler(SearchWorker worker) {
        this.worker = worker;
    }

    /** 返回动作描述。 */
    @Override
    public AgentAction action() {
        return new AgentAction("search", "网页搜索和只读远程信息查询",
                "{\"instruction\":\"string\"}", "text", AgentAction.SideEffectLevel.READ_ONLY);
    }

    /** 执行联网查询。 */
    @Override
    public AgentStepResult handle(AgentRunRequest request, AgentDecision decision, int stepIndex) {
        String message = WorkerPromptSupport.buildWorkerUserMessage(
                request.context().metadata().get("dialogueHistory"), decision.instruction());
        String observation = worker.run(message, request.context().workDir());
        return new AgentStepResult(stepIndex, action().id(), AgentStepResult.Status.SUCCEEDED,
                observation, null, Map.of("toolCalls", 1, "toolName", "web-search"));
    }
}
