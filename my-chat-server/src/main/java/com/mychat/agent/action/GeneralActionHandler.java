package com.mychat.agent.action;

import com.mychat.agent.api.AgentAction;
import com.mychat.agent.api.AgentRunRequest;
import com.mychat.agent.api.AgentStepResult;
import com.mychat.agent.runtime.AgentActionHandler;
import com.mychat.agent.runtime.AgentDecision;
import com.mychat.service.agent.worker.GeneralWorker;
import com.mychat.service.agent.worker.WorkerPromptSupport;
import org.springframework.stereotype.Component;

import java.util.Map;

/** 将现有 GeneralWorker 适配为通用 Action。 */
@Component
public class GeneralActionHandler implements AgentActionHandler {

    private final GeneralWorker worker;

    /** 注入现有 Worker。 */
    public GeneralActionHandler(GeneralWorker worker) {
        this.worker = worker;
    }

    /** 返回动作描述。 */
    @Override
    public AgentAction action() {
        return new AgentAction("general", "无工具的一般推理与写作", "{}", "text",
                AgentAction.SideEffectLevel.READ_ONLY);
    }

    /** 执行一般推理。 */
    @Override
    public AgentStepResult handle(AgentRunRequest request, AgentDecision decision, int stepIndex) {
        String message = WorkerPromptSupport.buildWorkerUserMessage(
                request.context().metadata().get("dialogueHistory"), decision.instruction());
        String observation = worker.run(message);
        return new AgentStepResult(stepIndex, action().id(), AgentStepResult.Status.SUCCEEDED,
                observation, null, Map.of("toolCalls", 0));
    }
}
