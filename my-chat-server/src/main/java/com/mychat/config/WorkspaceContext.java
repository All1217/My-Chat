package com.mychat.config;

import io.micrometer.context.ThreadLocalAccessor;

/**
 * 线程级工作目录上下文持有者。
 * 每个 HTTP 请求线程独立持有当前会话的工作目录路径，
 * 解决 WorkspaceUtil 全局单例可变状态的并发问题。
 * <p>
 * 通过 {@link WorkspaceContextAccessor} 配合
 * {@code Hooks.enableAutomaticContextPropagation()} 实现跨 reactive 线程传播。
 * <p>
 * 使用方式：
 * <pre>
 *   WorkspaceContext.set(workDir);       // Controller 中设置（Tomcat 线程）
 *   String dir = WorkspaceContext.get(); // ShellTool/WorkspaceUtil 中读取（Netty 线程亦可）
 *   WorkspaceContext.clear();            // 请求结束时清理
 * </pre>
 */
public class WorkspaceContext {

    public static final ThreadLocalAccessor<String> ACCESSOR = new WorkspaceContextAccessor();

    private static final ThreadLocal<String> WORK_DIR = new InheritableThreadLocal<>();

    public static void set(String workDir) {
        WORK_DIR.set(workDir);
    }

    public static String get() {
        return WORK_DIR.get();
    }

    public static void clear() {
        WORK_DIR.remove();
    }

    /** 获取当前工作区的目录名（最后一级），供系统提示使用 */
    public static String getWorkspaceName() {
        String dir = WORK_DIR.get();
        if (dir == null) return null;
        int lastSep = Math.max(dir.lastIndexOf('/'), dir.lastIndexOf('\\'));
        return lastSep >= 0 ? dir.substring(lastSep + 1) : dir;
    }

    /**
     * 注册到 Micrometer ContextRegistry，使 ThreadLocal 值随 Reactor Context 自动传播。
     */
    public static void registerAccessor() {
        WorkspaceContextAccessor.register();
    }

    /**
     * 适配 Micrometer Context Propagation 的 ThreadLocal 访问器。
     */
    private static final class WorkspaceContextAccessor implements ThreadLocalAccessor<String> {

        static final String KEY = WorkspaceContext.class.getName();

        @Override
        public Object key() {
            return KEY;
        }

        @Override
        public String getValue() {
            return WORK_DIR.get();
        }

        @Override
        @SuppressWarnings("unchecked")
        public void setValue(String value) {
            WORK_DIR.set(value);
        }

        @Override
        public void setValue() {
            WORK_DIR.remove();
        }

        @Override
        public void restore(String previousValue) {
            if (previousValue != null) {
                WORK_DIR.set(previousValue);
            } else {
                WORK_DIR.remove();
            }
        }

        static void register() {
            io.micrometer.context.ContextRegistry.getInstance()
                    .registerThreadLocalAccessor(new WorkspaceContextAccessor());
        }
    }
}
