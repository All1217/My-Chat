package com.mychat.agent.patterns;

import org.springframework.ai.chat.client.ChatClient;

import java.util.Locale;
import java.util.Set;

/**
 * Routing Workflow：先用一次 LLM 给输入打标签，再由调用方按标签分发。
 * <p>
 * <b>代码路径 vs 单次 LLM</b>：
 * <ul>
 *   <li>本类只负责「分类」这一次 LLM 调用（Structured Output）。</li>
 *   <li>选中哪条处理路径（file / kb / search / general）由 Java {@code switch} 决定，
 *       不属于模型自治 Agent。</li>
 * </ul>
 * 对照：06-agents 用同一 ChatClient 换 system prompt；My-Chat 阶段 E 在分类后换不同 ChatClient。
 */
public class RoutingWorkflow {

    /** 允许的路由标签（与 AgentRoutingService 分发一致） */
    public static final Set<String> ALLOWED_ROUTES = Set.of("file", "kb", "search", "general");

    /**
     * 分类器 Structured Output 形态。
     *
     * @param reasoning 为何选该标签
     * @param selection 标签名，须为 ALLOWED_ROUTES 之一
     */
    public record RoutingResponse(String reasoning, String selection) {
    }

    private final ChatClient chatClient;

    public RoutingWorkflow(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * 对用户输入做路由分类。
     *
     * @param input 用户原文
     * @return 规范化后的标签与推理；未知标签回退为 general
     */
    @SuppressWarnings("null")
    public RoutingResponse determineRoute(String input) {
        // 字面量花括号须写成 \{ \}，否则 StTemplateRenderer 会把 JSON 示例当成模板变量
        String selectorPrompt = """
                分析用户输入，从下列路由中选择最合适的一个：
                - file：需要查看、创建、修改、删除工作区文件或目录
                - kb：需要根据已有知识库文档回答（检索问答）
                - search：需要联网搜索、查天气或其它远程 MCP 信息工具
                - general：普通闲聊或无需文件/知识库/联网的一般问答

                先简要说明理由，再按以下 JSON 返回（selection 必须是上述四个英文标签之一）：
                \\{
                  "reasoning": "简要理由",
                  "selection": "file|kb|search|general"
                \\}

                用户输入：
                {input}
                """;

        RoutingResponse raw = chatClient.prompt()
                .user(u -> u.text(selectorPrompt).param("input", input))
                // DeepSeek 等兼容端不支持 provider-native response_format；
                // 使用默认 prompt 式 .entity() 解析 JSON（勿加 ENABLE_NATIVE_STRUCTURED_OUTPUT）
                .call()
                .entity(RoutingResponse.class);

        String selection = normalizeSelection(raw != null ? raw.selection() : null);
        String reasoning = raw != null && raw.reasoning() != null ? raw.reasoning() : "";

        if (!ALLOWED_ROUTES.contains(selection)) {
            reasoning = (reasoning.isBlank() ? "" : reasoning + " ")
                    + "[分类器返回未知标签，已回退为 general]";
            selection = "general";
        }

        return new RoutingResponse(reasoning, selection);
    }

    private static String normalizeSelection(String selection) {
        if (selection == null || selection.isBlank()) {
            return "general";
        }
        return selection.trim().toLowerCase(Locale.ROOT);
    }
}
