package com.mychat.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Hooks;

/**
 * 启用 Reactor 的自动上下文传播，使 WorkspaceContext 的 ThreadLocal 值
 * 能够跨越 Tomcat 虚拟线程 → Netty reactor 线程的边界。
 * <p>
 * 这是解决 ShellTool（运行在 Netty 线程上）无法读取
 * ChatController（运行在 Tomcat 虚拟线程上）设置的
 * WorkspaceContext 的唯一正确方案。
 */
@Slf4j
@Configuration
public class ContextPropagationConfiguration {

    @PostConstruct
    void enableContextPropagation() {
        WorkspaceContext.registerAccessor();
        Hooks.enableAutomaticContextPropagation();
        log.info("Reactor 自动上下文传播已启用，WorkspaceContext ThreadLocal 已注册");
    }
}
