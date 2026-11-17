package com.mychat.apps.papertrade.audit;

import com.mychat.apps.papertrade.audit.entity.po.PaperAiToolAudit;
import com.mychat.apps.papertrade.audit.mapper.PaperAiToolAuditMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

/**
 * 仅当 Agent 工具链 ThreadLocal 激活时，记录 papertrade 写库 @Tool。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class PaperAiToolAuditAspect {

    private final PaperAiToolAuditMapper auditMapper;

    /** 拦截 papertrade.tools 包中的 @Tool 写方法。 */
    @Around("execution(* com.mychat.apps.papertrade.tools..*(..)) && @annotation(tool)")
    public Object aroundWriteTool(ProceedingJoinPoint pjp, Tool tool) throws Throwable {
        Method method = ((MethodSignature) pjp.getSignature()).getMethod();
        String name = method.getName();
        if (!isWriteMethod(name) || !AgentToolAuditContext.active()) {
            return pjp.proceed();
        }
        Object result = null;
        Throwable error = null;
        try {
            result = pjp.proceed();
            return result;
        } catch (Throwable t) {
            error = t;
            throw t;
        } finally {
            try {
                save(name, pjp.getArgs(), result, error);
            } catch (Exception e) {
                log.debug("工具审计写入失败: {}", e.getMessage());
            }
        }
    }

    private void save(String methodName, Object[] args, Object result, Throwable error) {
        PaperAiToolAudit row = new PaperAiToolAudit();
        row.setId(UUID.randomUUID().toString().replace("-", ""));
        row.setScenarioId(AgentToolAuditContext.scenarioId());
        row.setAgentRunId(AgentToolAuditContext.agentRunId());
        row.setToolName(methodName);
        row.setTableName(tableOf(methodName));
        row.setOp(opOf(methodName));
        row.setSymbol(firstSymbol(args));
        row.setArgsJson(clip(String.valueOf(args == null ? "" : java.util.Arrays.toString(args)), 2000));
        row.setResultJson(clip(result == null ? null : String.valueOf(result), 2000));
        row.setSuccess(error == null);
        row.setErrorMessage(error == null ? null : clip(error.getMessage(), 500));
        row.setCreatedAt(LocalDateTime.now());
        auditMapper.insert(row);
    }

    static boolean isWriteMethod(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.startsWith("create")
                || n.startsWith("update")
                || n.startsWith("delete")
                || n.startsWith("upsert")
                || n.startsWith("record")
                || n.startsWith("place");
    }

    private static String tableOf(String method) {
        String m = method.toLowerCase(Locale.ROOT);
        if (m.contains("stage")) {
            return "paper_stage_summary";
        }
        if (m.contains("decision")) {
            return "paper_ai_decision";
        }
        if (m.contains("order") || m.contains("place")) {
            return "paper_order";
        }
        return "paper_research_note";
    }

    private static String opOf(String method) {
        String m = method.toLowerCase(Locale.ROOT);
        if (m.startsWith("create") || m.startsWith("upsert") || m.startsWith("record") || m.startsWith("place")) {
            return "WRITE";
        }
        if (m.startsWith("delete")) {
            return "DELETE";
        }
        return "UPDATE";
    }

    private static String firstSymbol(Object[] args) {
        if (args == null) {
            return null;
        }
        for (Object a : args) {
            if (a instanceof String s && s.matches("\\d{6}")) {
                return s;
            }
        }
        return null;
    }

    private static String clip(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max);
    }
}
