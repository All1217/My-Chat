package com.mychat.agent.action;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mychat.agent.api.AgentAction;
import com.mychat.agent.api.AgentRunRequest;
import com.mychat.agent.api.AgentStepResult;
import com.mychat.agent.runtime.AgentActionHandler;
import com.mychat.agent.runtime.AgentDecision;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** INTERVIEWER 的动态追问与结构化评分动作。 */
@Component
public class InterviewerActionHandler implements AgentActionHandler {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    /** 注入无工具工作流客户端和 JSON 编解码器。 */
    public InterviewerActionHandler(
            @Qualifier("agentWorkflowChatClient") ChatClient chatClient,
            ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.objectMapper = objectMapper;
    }

    /** 返回面试动作描述。 */
    @Override
    public AgentAction action() {
        return new AgentAction(
                "interview",
                "按面试阶段追问并输出结构化评分",
                "{\"phase\":\"INTRO|TECHNICAL|SCENARIO|WRAP_UP\",\"prompt\":\"string\"}",
                "interview_assessment",
                AgentAction.SideEffectLevel.READ_ONLY);
    }

    /** 执行当前面试阶段并返回可人工复核的结构化结果。 */
    @Override
    public AgentStepResult handle(
            AgentRunRequest request, AgentDecision decision, int stepIndex) {
        String phase = String.valueOf(
                decision.parameters().getOrDefault("phase", "TECHNICAL"));
        String prompt = String.valueOf(
                decision.parameters().getOrDefault("prompt", decision.instruction()));
        InterviewAssessment assessment = chatClient.prompt()
                .system(systemPrompt(phase))
                .user(ActionPromptSupport.message(request, new com.mychat.agent.runtime.AgentDecision(
                        decision.actionId(), prompt, decision.parameters(),
                        false, "", decision.reason())))
                .advisors(advisor -> advisor.param(
                        ChatMemory.CONVERSATION_ID,
                        "interview:" + request.context().conversationId()))
                .call()
                .entity(InterviewAssessment.class);
        InterviewAssessment safe = assessment == null
                ? new InterviewAssessment(phase, "", 0, List.of(), true)
                : assessment;
        return new AgentStepResult(
                stepIndex, action().id(), AgentStepResult.Status.SUCCEEDED,
                json(safe), null,
                Map.of(
                        "toolCalls", 0,
                        "phase", phase,
                        "manualReview", safe.manualReview()));
    }

    /** 构建固定阶段约束。 */
    private String systemPrompt(String phase) {
        return """
                你是结构化技术面试官。当前固定阶段：%s。
                根据候选人上一轮回答动态生成一个追问，并按 0-100 评分。
                输出 InterviewAssessment；评分依据必须简洁可复核。
                INTRO 只做背景确认，TECHNICAL 检查技术深度，
                SCENARIO 检查问题解决能力，WRAP_UP 汇总并标记是否需要人工复核。
                """.formatted(phase);
    }

    /** 序列化结构化评分。 */
    private String json(InterviewAssessment assessment) {
        try {
            return objectMapper.writeValueAsString(assessment);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("面试评分序列化失败", exception);
        }
    }

    /** 面试阶段输出契约。 */
    public record InterviewAssessment(
            String phase,
            String followUpQuestion,
            int score,
            List<String> rubricFindings,
            boolean manualReview) {
    }
}
