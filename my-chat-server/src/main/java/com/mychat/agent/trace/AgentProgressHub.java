package com.mychat.agent.trace;

import com.mychat.agent.api.PublicProgressEvent;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** 将 Runtime 公开进度路由到当前主聊天流。 */
@Component
public class AgentProgressHub {

    private final Map<String, Consumer<PublicProgressEvent>> consumers = new ConcurrentHashMap<>();

    /** 为 Run 注册临时公开事件消费者。 */
    public AutoCloseable register(String runId, Consumer<PublicProgressEvent> consumer) {
        consumers.put(runId, consumer);
        return () -> consumers.remove(runId, consumer);
    }

    /** 发布公开进度；没有在线消费者时只保留持久化 Trace。 */
    public void publish(PublicProgressEvent event) {
        Consumer<PublicProgressEvent> consumer = consumers.get(event.runId());
        if (consumer != null) {
            consumer.accept(event);
        }
    }
}
