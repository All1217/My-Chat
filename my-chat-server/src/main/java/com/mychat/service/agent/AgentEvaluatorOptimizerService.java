package com.mychat.service.agent;

import com.mychat.common.ChatStreamEvent;
import com.mychat.common.EvaluatorOptimizerWorkflow;
import com.mychat.config.WorkspaceContext;
import com.mychat.entity.dto.EvaluateOptimizeRequest;
import com.mychat.utils.WorkspacePromptBuilder;
import com.mychat.utils.WorkspaceUtil;
import com.mychat.vo.EvaluateOptimizeResultVO;
import com.mychat.vo.EvaluateOptimizeRoundVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Evaluator-Optimizer 服务：任务内质量环（写盘 → 读回 → 评价 → 不合格再改）。
 * <p>
 * 用于单次任务中提升产物质量；不是离线 Agent 评测 / Benchmark。
 * 生成侧用 {@code toolChatClient}；评价侧用无工具 {@code agentWorkflowChatClient}；
 * 读回以 {@link WorkspaceUtil#readFileContent} 为事实源。
 */
@Slf4j
@Service
public class AgentEvaluatorOptimizerService {

    public static final int DEFAULT_MAX_ITERATIONS = 3;
    public static final int MIN_MAX_ITERATIONS = 1;
    public static final int HARD_MAX_ITERATIONS = 5;

    private final EvaluatorOptimizerWorkflow evaluatorWorkflow;
    private final ChatClient toolChatClient;
    private final WorkspaceUtil workspaceUtil;
    private final WorkspacePromptBuilder workspacePromptBuilder;

    public AgentEvaluatorOptimizerService(
            @Qualifier("agentWorkflowChatClient") ChatClient agentWorkflowChatClient,
            @Qualifier("toolChatClient") ChatClient toolChatClient,
            WorkspaceUtil workspaceUtil,
            WorkspacePromptBuilder workspacePromptBuilder) {
        this.toolChatClient = toolChatClient;
        this.workspaceUtil = workspaceUtil;
        this.workspacePromptBuilder = workspacePromptBuilder;
        this.evaluatorWorkflow = new EvaluatorOptimizerWorkflow(agentWorkflowChatClient);
    }

    /**
     * 执行任务内质量环直至达标或达到 maxIterations。
     *
     * @throws IllegalArgumentException 必填参数缺失
     */
    public EvaluateOptimizeResultVO evaluateOptimize(EvaluateOptimizeRequest request) {
        // —— 1. 校验入参 ——
        if (request == null || !StringUtils.hasText(request.getGoal())) {
            throw new IllegalArgumentException("goal 不能为空");
        }
        if (!StringUtils.hasText(request.getPath())) {
            throw new IllegalArgumentException("path 不能为空");
        }
        if (!StringUtils.hasText(request.getCriteria())) {
            throw new IllegalArgumentException("criteria 不能为空");
        }

        // —— 2. 规范化目标/路径/标准/工作目录/迭代上限 ——
        String goal = request.getGoal().trim();
        String path = request.getPath().trim().replace("\\", "/");
        String criteria = request.getCriteria().trim();
        List<String> mustContain = request.getMustContain() != null
                ? request.getMustContain()
                : List.of();
        String workDir = StringUtils.hasText(request.getWorkDir())
                ? request.getWorkDir().trim()
                : workspaceUtil.getWorkspaceRoot().toString();
        int maxIterations = clampMaxIterations(request.getMaxIterations());

        // —— 3. 准备本轮状态（各轮记录、上一轮反馈、最后读到的正文） ——
        List<EvaluateOptimizeRoundVO> rounds = new ArrayList<>();
        String lastFeedback = null; // 真正给大模型看的反馈信息
        String lastContent = "";
        boolean passed = false;

        // —— 4. 绑定工作区，进入「写→读→评→改」循环 ——
        WorkspaceContext.set(workDir);
        try {
            for (int iter = 1; iter <= maxIterations; iter++) {
                log.info("Evaluator-Optimizer iteration={}/{} path={}", iter, maxIterations, path);

                // 4a. 生成器：按目标/标准（及上轮意见）调用工具写盘覆盖 path
                // 执行修改意见也在此
                String generatorSummary = runGenerator(goal, path, criteria, lastFeedback, workDir, iter);

                // 4b. 读回：以磁盘文件为事实源；读失败则记一轮并带反馈重试
                String fileContent;
                String fileSnapshot;
                try {
                    fileContent = workspaceUtil.readFileContent(path);
                    fileSnapshot = truncate(fileContent);
                    lastContent = truncate(fileContent);
                } catch (Exception e) {
                    String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    lastFeedback = "未写入目标路径或无法读取: " + path + "（" + msg + "）。请使用 write 工具写入该相对路径。";
                    rounds.add(new EvaluateOptimizeRoundVO(
                            iter,
                            generatorSummary,
                            "",
                            false,
                            false,
                            lastFeedback,
                            "read failed"));
                    continue;
                }

                // 4c. 硬规则：空文件 / mustContain 子串；不通过则跳过模型评价，直接下一轮改写
                RuleCheck ruleCheck = applyHardRules(fileContent, mustContain);
                if (!ruleCheck.passed()) {
                    lastFeedback = ruleCheck.feedback();
                    rounds.add(new EvaluateOptimizeRoundVO(
                            iter,
                            generatorSummary,
                            fileSnapshot,
                            false,
                            false,
                            lastFeedback,
                            ruleCheck.reasoning()));
                    continue;
                }

                // 4d. 模型评价：对照 goal/criteria 给 pass + feedback
                EvaluatorOptimizerWorkflow.Evaluation evaluation = evaluatorWorkflow.evaluate(
                        goal, criteria, path, fileSnapshot, iter);
                rounds.add(new EvaluateOptimizeRoundVO(
                        iter,
                        generatorSummary,
                        fileSnapshot,
                        true,
                        evaluation.pass(),
                        evaluation.feedback(),
                        evaluation.reasoning()));

                // 4e. 通过则结束；否则把意见留给下一轮生成器
                if (evaluation.pass()) {
                    passed = true;
                    break;
                }
                lastFeedback = evaluation.feedback(); // 评价失败
            }
        } finally {
            // —— 5. 无论成败都清掉工作区 ThreadLocal ——
            WorkspaceContext.clear();
        }

        // —— 6. 汇总返回：是否达标、结束原因、正文快照、各轮明细 ——
        String finishedReason = passed ? "passed" : "max_iterations";
        return new EvaluateOptimizeResultVO(passed, finishedReason, path, lastContent, rounds);
    }

    private String runGenerator(
            String goal,
            String path,
            String criteria,
            String previousFeedback,
            String workDir,
            int iteration) {
        String conversationId = "eval-opt-" + UUID.randomUUID() + "-" + iteration;

        // 路径规则 + 浅层摘要 + 质量环写盘指令
        String system = workspacePromptBuilder.build(workDir) + """
                
                你是任务内质量环的生成器。必须调用 write 工具，将完整文件内容写入相对路径 "%s"（覆盖写入）。
                不要只口头描述；不调用 write 视为失败。
                """.formatted(path);

        StringBuilder user = new StringBuilder();
        user.append("目标：\n").append(goal).append("\n\n");
        user.append("质量标准：\n").append(criteria).append("\n\n");
        user.append("目标文件路径（必须 write 到此路径）：").append(path).append("\n");
        if (StringUtils.hasText(previousFeedback)) {
            user.append("\n上一轮未通过，请根据以下意见覆盖重写同一文件：\n")
                    .append(previousFeedback).append("\n");
        }

        try {
            String content = toolChatClient.prompt()
                    .system(system)
                    .user(user.toString())
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                    .call()
                    .content();
            return truncate(content != null ? content : "(generator empty)");
        } catch (Exception e) {
            log.warn("Generator 失败 iter={}: {}", iteration, e.getMessage());
            return "[Generator 错误] " + (e.getMessage() != null
                    ? e.getMessage()
                    : e.getClass().getSimpleName());
        }
    }

    private static RuleCheck applyHardRules(String content, List<String> mustContain) {
        if (content == null || content.isBlank()) {
            return new RuleCheck(false, "文件为空，请写入非空内容。", "empty file");
        }
        if (mustContain != null) {
            for (String token : mustContain) {
                if (!StringUtils.hasText(token)) {
                    continue;
                }
                if (!content.contains(token)) {
                    return new RuleCheck(
                            false,
                            "硬规则未通过：内容缺少必含子串 \"" + token + "\"。请补全后重新 write。",
                            "missing mustContain: " + token);
                }
            }
        }
        return new RuleCheck(true, "", "ok");
    }

    static int clampMaxIterations(Integer requested) {
        if (requested == null) {
            return DEFAULT_MAX_ITERATIONS;
        }
        return Math.max(MIN_MAX_ITERATIONS, Math.min(HARD_MAX_ITERATIONS, requested));
    }

    static String truncate(String raw) {
        if (raw == null) {
            return "";
        }
        if (raw.length() <= ChatStreamEvent.PREVIEW_MAX_CHARS) {
            return raw;
        }
        return raw.substring(0, ChatStreamEvent.PREVIEW_MAX_CHARS) + "\n…[已截断]";
    }

    private record RuleCheck(boolean passed, String feedback, String reasoning) {
    }
}
