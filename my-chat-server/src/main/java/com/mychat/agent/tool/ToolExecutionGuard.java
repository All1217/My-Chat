package com.mychat.agent.tool;

import com.mychat.agent.api.AgentAction;
import com.mychat.agent.api.AgentScenarioProfile;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** 在工具边界统一执行权限、审批、参数、超时与结果尺寸限制。 */
@Component
public class ToolExecutionGuard {

    private static final int MAX_PARAMETER_CHARS = 32_000;
    private static final int MAX_RESULT_CHARS = 64_000;
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    private final ToolPolicy policy;

    /** 注入工具策略。 */
    public ToolExecutionGuard(ToolPolicy policy) {
        this.policy = policy;
    }

    /** 校验并限时执行一次工具调用。 */
    public String execute(AgentScenarioProfile profile, String toolId, Map<String, Object> parameters,
                          boolean approved, Duration timeout, Callable<String> invocation) {
        ToolDescriptor descriptor = policy.requireAllowed(profile, toolId);
        String serializedParameters = String.valueOf(parameters == null ? Map.of() : parameters);
        if (serializedParameters.length() > MAX_PARAMETER_CHARS) {
            throw new IllegalArgumentException("工具参数超过尺寸限制");
        }
        if (descriptor.sideEffectLevel() != AgentAction.SideEffectLevel.READ_ONLY && !approved) {
            throw new SecurityException("写工具等待批准: " + toolId);
        }

        Duration effectiveTimeout = timeout == null ? DEFAULT_TIMEOUT : timeout;
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            String result = executor.submit(invocation).get(effectiveTimeout.toMillis(), TimeUnit.MILLISECONDS);
            if (result != null && result.length() > MAX_RESULT_CHARS) {
                return result.substring(0, MAX_RESULT_CHARS) + "\n[结果已截断]";
            }
            return result == null ? "" : result;
        } catch (TimeoutException exception) {
            throw new IllegalStateException("工具执行超时: " + toolId, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("工具执行被中断: " + toolId, exception);
        } catch (Exception exception) {
            throw new IllegalStateException("工具执行失败: " + toolId + ": " + rootMessage(exception), exception);
        } finally {
            executor.shutdownNow();
        }
    }

    /** 提取最内层错误消息。 */
    private String rootMessage(Exception exception) {
        Throwable cause = exception;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage();
    }
}
