package com.mychat.service.agent.workflow;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Set;

/**
 * Routing 分类器：一次 LLM 打标签，再由调用方 Java switch 分发。仅 Demo 使用。
 * <p>
 * 主聊天走 {@link OrchestratorWorkflow} 多步决策，不经过本类。
 */
public class RoutingWorkflow {

    /** 允许的路由标签（与 {@link com.mychat.service.agent.demo.AgentRoutingService} 分发一致） */
    public static final Set<String> ALLOWED_ROUTES = Set.of("file", "kb", "search", "general");

    /**
     * 分类器 Structured Output 形态。
     *
     * @param reasoning 为何选该标签
     * @param selection 标签名，须为 ALLOWED_ROUTES 之一
     */
    public record RoutingResponse(String reasoning, String selection) {
    }

    /**
     * 会话侧约束，供分类 prompt 与调用方后置校验共用。
     *
     * @param kbId    已绑定知识库 ID（可空）
     * @param workDir 会话工作目录（可空；空时仍可用默认工作区）
     */
    public record RouteContext(String kbId, String workDir) {
        public boolean hasKb() {
            return StringUtils.hasText(kbId);
        }

        public boolean hasWorkDir() {
            return StringUtils.hasText(workDir);
        }
    }

    private final ChatClient chatClient;

    public RoutingWorkflow(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * 对用户输入做路由分类（无会话约束）。
     */
    public RoutingResponse determineRoute(String input) {
        return determineRoute(input, new RouteContext(null, null));
    }

    /**
     * 对用户输入做路由分类，并注入会话上下文提示。
     *
     * @param input   用户原文
     * @param context 可选 kbId / workDir
     * @return 规范化后的标签与推理；未知标签回退为 general
     */
    @SuppressWarnings("null")
    public RoutingResponse determineRoute(String input, RouteContext context) {
        RouteContext ctx = context != null ? context : new RouteContext(null, null);

        String sessionHints = buildSessionHints(ctx);

        // 字面量花括号须写成 \{ \}，否则 StTemplateRenderer 会把 JSON 示例当成模板变量
        String selectorPrompt = """
                分析用户输入，从下列路由中选择最合适的一个：
                - file：需要查看、创建、修改、删除工作区文件或目录
                - kb：需要根据已有知识库文档回答（检索问答）
                - search：需要联网搜索、查天气或其它远程 MCP 信息工具
                - general：普通闲聊或无需文件/知识库/联网的一般问答

                会话上下文：
                {sessionHints}

                先简要说明理由，再按以下 JSON 返回（selection 必须是上述四个英文标签之一）：
                \\{
                  "reasoning": "简要理由",
                  "selection": "file|kb|search|general"
                \\}

                用户输入：
                {input}
                """;

        RoutingResponse raw = chatClient.prompt()
                .user(u -> u.text(selectorPrompt)
                        .param("sessionHints", sessionHints)
                        .param("input", input))
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

    private static String buildSessionHints(RouteContext ctx) {
        StringBuilder sb = new StringBuilder();
        if (ctx.hasKb()) {
            sb.append("- 当前会话已绑定知识库（kbId=").append(ctx.kbId().trim())
                    .append("）。若问题需要基于文档检索回答，优先选 kb；")
                    .append("若明确要操作文件选 file；若要联网/天气选 search；纯闲聊选 general。\n");
        } else {
            sb.append("- 当前会话未绑定知识库：不要选择 kb")
                    .append("（若用户像在问某份文档，请选 general）。\n");
        }
        if (ctx.hasWorkDir()) {
            sb.append("- 当前会话工作目录：").append(ctx.workDir().trim())
                    .append("。涉及该目录下文件操作时选 file。\n");
        } else {
            sb.append("- 未指定自定义工作目录，仍可使用默认工作区；文件相关问题选 file。\n");
        }
        return sb.toString().trim();
    }

    private static String normalizeSelection(String selection) {
        if (selection == null || selection.isBlank()) {
            return "general";
        }
        return selection.trim().toLowerCase(Locale.ROOT);
    }
}
