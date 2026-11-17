package com.mychat.agent.runtime;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 动态动作注册表；新增业务动作无需修改 AgentLoop。 */
@Component
public class ActionRegistry {

    private final Map<String, AgentActionHandler> handlers;

    /** 收集所有 Handler 并拒绝重复 actionId。 */
    public ActionRegistry(List<AgentActionHandler> handlers) {
        Map<String, AgentActionHandler> indexed = new LinkedHashMap<>();
        for (AgentActionHandler handler : handlers == null ? List.<AgentActionHandler>of() : handlers) {
            String id = handler.action().id();
            if (indexed.putIfAbsent(id, handler) != null) {
                throw new IllegalStateException("重复 Agent actionId: " + id);
            }
        }
        this.handlers = Map.copyOf(indexed);
    }

    /** 获取动作处理器。 */
    public AgentActionHandler require(String actionId) {
        AgentActionHandler handler = handlers.get(actionId);
        if (handler == null) {
            throw new IllegalArgumentException("未注册 Agent action: " + actionId);
        }
        return handler;
    }

    /** 判断动作是否存在。 */
    public boolean contains(String actionId) {
        return handlers.containsKey(actionId);
    }

    /** 返回已注册动作快照。 */
    public Collection<AgentActionHandler> all() {
        return handlers.values();
    }
}
