package com.mychat.agent.action;

import com.mychat.agent.api.AgentAction;
import com.mychat.agent.api.AgentRunRequest;
import com.mychat.agent.api.AgentStepResult;
import com.mychat.agent.runtime.AgentActionHandler;
import com.mychat.agent.runtime.AgentDecision;
import com.mychat.service.agent.worker.FileWorker;
import com.mychat.service.agent.worker.WorkerPromptSupport;
import org.springframework.stereotype.Component;

import java.util.Map;

/** 将现有 FileWorker 适配为需批准的文件 Action。 */
@Component
public class FileActionHandler implements AgentActionHandler {

    private final FileWorker worker;

    /** 注入现有 Worker。 */
    public FileActionHandler(FileWorker worker) {
        this.worker = worker;
    }

    /** 返回动作描述。 */
    @Override
    public AgentAction action() {
        return new AgentAction("file", "读取或修改工作区文件", "{\"instruction\":\"string\"}",
                "text", AgentAction.SideEffectLevel.HIGH_RISK_WRITE);
    }

    /** 在显式工作目录执行文件任务。 */
    @Override
    public AgentStepResult handle(AgentRunRequest request, AgentDecision decision, int stepIndex) {
        String message = WorkerPromptSupport.buildWorkerUserMessage(
                request.context().metadata().get("dialogueHistory"), decision.instruction());
        String observation = worker.run(message, request.context().workDir());
        return new AgentStepResult(stepIndex, action().id(), AgentStepResult.Status.SUCCEEDED,
                observation, null, Map.of("toolCalls", 1, "toolName", "file"));
    }
}
