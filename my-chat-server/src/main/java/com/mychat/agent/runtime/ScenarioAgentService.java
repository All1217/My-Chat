package com.mychat.agent.runtime;

import com.mychat.agent.api.AgentRunContext;
import com.mychat.agent.api.AgentRunRequest;
import com.mychat.agent.api.AgentRunResult;
import com.mychat.agent.api.AgentScenarioId;
import com.mychat.agent.api.AgentScenarioProfile;
import com.mychat.agent.context.ScenarioContextFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Phase 5 多场景调用入口，不在 AgentRuntime 中增加场景分支。 */
@Service
public class ScenarioAgentService {

    private final AgentRuntime runtime;
    private final ScenarioProfileRegistry profiles;
    private final ScenarioContextFactory contextFactory;

    /** 注入通用 Runtime、Profile 注册表和上下文工厂。 */
    public ScenarioAgentService(
            AgentRuntime runtime,
            ScenarioProfileRegistry profiles,
            ScenarioContextFactory contextFactory) {
        this.runtime = runtime;
        this.profiles = profiles;
        this.contextFactory = contextFactory;
    }

    /** 根据 Profile 构建显式上下文并同步执行。 */
    public AgentRunResult run(ScenarioRunCommand command) {
        if (command == null || command.scenarioId() == null
                || !StringUtils.hasText(command.input())) {
            throw new IllegalArgumentException("scenarioId 和 input 不能为空");
        }
        AgentScenarioProfile profile = profiles.require(command.scenarioId());
        String runId = "run-" + UUID.randomUUID().toString().replace("-", "");
        String conversationId = StringUtils.hasText(command.conversationId())
                ? command.conversationId().trim() : runId;
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("contextText", contextFactory.render(
                profile, command.input(), command.rubric(), command.attributes()));
        parameters.put("maxDurationMs", 180_000L);
        parameters.put("maxTokens", 16_000L);
        if (StringUtils.hasText(command.rubric())) {
            parameters.put("rubric", command.rubric().trim());
        }
        parameters.putAll(command.attributes() == null ? Map.of() : command.attributes());
        AgentRunContext context = new AgentRunContext(
                runId,
                command.scenarioId(),
                conversationId,
                command.userId(),
                null,
                command.workDir(),
                command.businessRef(),
                Map.of(
                        "memoryPolicy", profile.memoryPolicy(),
                        "contextPolicy", profile.contextPolicy(),
                        "qualityPolicy", profile.qualityPolicy()));
        return runtime.run(new AgentRunRequest(command.input(), context, parameters));
    }

    /** 多场景调用参数。 */
    public record ScenarioRunCommand(
            AgentScenarioId scenarioId,
            String input,
            String conversationId,
            String userId,
            String businessRef,
            String workDir,
            String rubric,
            Map<String, Object> attributes) {

        /** 固化可选属性。 */
        public ScenarioRunCommand {
            attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        }
    }
}
