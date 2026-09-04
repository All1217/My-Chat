package com.mychat.service.agent;

import com.mychat.service.agent.workflow.OrchestratorWorkflow;
import com.mychat.vo.OrchestrateStepVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 弱最终答复检测、observation 合成与单步快路径门控（不依赖 Spring 上下文）。
 */
class AgentOrchestratorFinalAnswerTest {

    @Test
    void metaOutlineIsWeakAndGetsComposed() {
        String outline = "结合知识库定义和搜索到的实践案例，向用户完整作答：首先给出Java三大特性，接着提供案例代码。";
        String kb = """
                ## 一、封装
                定义：隐藏细节。
                """;
        String search = """
                ## 案例
                ```java
                class Animal {}
                ```
                """;
        List<OrchestrateStepVO> steps = List.of(
                new OrchestrateStepVO(1, "retrieve_kb", "r", "i1", kb),
                new OrchestrateStepVO(2, "search", "r", "i2", search)
        );

        int obsChars = kb.length() + search.length();
        assertTrue(AgentOrchestratorService.isWeakFinalAnswer(outline, obsChars, steps));

        String answer = AgentOrchestratorService.resolveFinalAnswer(
                "根据知识库回答Java三大特性并联网搜索案例", outline, steps);
        assertTrue(answer.contains("知识库要点"));
        assertTrue(answer.contains("联网补充"));
        assertTrue(answer.contains("封装") || answer.contains("Animal"));
        assertFalse(answer.contains("向用户完整作答"));
    }

    @Test
    void strongFinishKeptAsIs() {
        String strong = """
                ## Java 三大特性

                - **封装**：隐藏实现
                - **继承**：复用父类
                - **多态**：同一接口多种实现

                ```java
                class Demo {}
                ```
                """;
        List<OrchestrateStepVO> steps = List.of(
                new OrchestrateStepVO(1, "retrieve_kb", "r", "i", "少量摘录")
        );
        assertFalse(AgentOrchestratorService.isWeakFinalAnswer(strong, 10, steps));
        String answer = AgentOrchestratorService.resolveFinalAnswer("问三大特性", strong, steps);
        assertTrue(answer.startsWith("## Java 三大特性"));
    }

    @Test
    void streamFinalPromptContainsGoalDraftAndMaterials() {
        List<OrchestrateStepVO> steps = List.of(
                new OrchestrateStepVO(1, "file", "r", "列出目录", "根目录含 src/ 与 README.md")
        );
        String prompt = AgentOrchestratorService.buildFinalAnswerStreamUserPrompt(
                "查看项目结构", "## 草稿\n- src", steps, null);
        assertTrue(prompt.contains("用户目标："));
        assertTrue(prompt.contains("查看项目结构"));
        assertTrue(prompt.contains("文件结果"));
        assertTrue(prompt.contains("README.md"));
        assertTrue(prompt.contains("答复草稿："));
        assertTrue(prompt.contains("请直接输出最终答复："));
        assertTrue(prompt.contains("近期原文"));
        assertTrue(prompt.contains("（无）"));
    }

    @Test
    void streamFinalPromptIncludesDialogueHistory() {
        List<OrchestrateStepVO> steps = List.of(
                new OrchestrateStepVO(1, "general", "r", "i", "ok")
        );
        String prompt = AgentOrchestratorService.buildFinalAnswerStreamUserPrompt(
                "在刚才那个文件末尾加一行", "草稿", steps,
                "USER: 把备注写入 notes/todo.md\nASSISTANT: 已写入");
        assertTrue(prompt.contains("近期原文"));
        assertTrue(prompt.contains("notes/todo.md"));
    }

    @Test
    void workerUserMessagePrefixesHistoryAndTask() {
        String msg = AgentOrchestratorService.buildWorkerUserMessage(
                "USER: 写入 notes/todo.md\nASSISTANT: 已完成",
                "在该文件末尾追加 done");
        assertTrue(msg.contains("【会话上下文"));
        assertTrue(msg.contains("notes/todo.md"));
        assertTrue(msg.contains("【本步任务】"));
        assertTrue(msg.contains("在该文件末尾追加 done"));
    }

    @Test
    void workerUserMessageEmptyHistoryShowsPlaceholder() {
        String msg = AgentOrchestratorService.buildWorkerUserMessage(null, "列出根目录");
        assertTrue(msg.contains("（无）"));
        assertTrue(msg.contains("列出根目录"));
    }

    @Test
    void workerUserMessageTruncatesLongHistory() {
        String longHist = "x".repeat(AgentOrchestratorService.WORKER_DIALOGUE_HISTORY_MAX_CHARS + 50);
        String msg = AgentOrchestratorService.buildWorkerUserMessage(longHist, "task");
        assertTrue(msg.contains("…[会话摘要已截断]"));
        assertTrue(msg.length() < longHist.length() + 200);
    }

    @Test
    void truncateDialoguePrefersSummaryBlock() {
        String summary = "【会话摘要｜较早轮次压缩，可能有损】\n"
                + "用户约定 path=src/App.vue；结论：已改路由。\n\n";
        String recent = "【近期对话原文】\n"
                + "用户：" + "y".repeat(AgentOrchestratorService.WORKER_DIALOGUE_HISTORY_MAX_CHARS)
                + "\n助手：ok\n";
        String full = summary + recent;
        String truncated = AgentOrchestratorService.truncateDialogueForWorker(full);
        assertTrue(truncated.contains("path=src/App.vue"));
        assertTrue(truncated.contains("【会话摘要"));
        assertTrue(truncated.contains("…[近期原文已截断]")
                || truncated.length() <= AgentOrchestratorService.WORKER_DIALOGUE_HISTORY_MAX_CHARS + 40);
        assertTrue(truncated.indexOf("【会话摘要") < truncated.indexOf("【近期对话原文】"));
    }

    @Test
    void normalizeComplexityDefaultsToMulti() {
        assertEquals("multi", OrchestratorWorkflow.normalizeComplexity(null));
        assertEquals("multi", OrchestratorWorkflow.normalizeComplexity(""));
        assertEquals("multi", OrchestratorWorkflow.normalizeComplexity("unknown"));
        assertEquals("single", OrchestratorWorkflow.normalizeComplexity("single"));
        assertEquals("single", OrchestratorWorkflow.normalizeComplexity("SINGLE"));
        assertEquals("multi", OrchestratorWorkflow.normalizeComplexity("multi"));
    }

    @Test
    void shouldAutoFinishOnlyForSuccessfulSingleShot() {
        assertTrue(AgentOrchestratorService.shouldAutoFinishAfterWorker(
                "single", "## 封装\n隐藏实现细节"));
        assertFalse(AgentOrchestratorService.shouldAutoFinishAfterWorker(
                "multi", "## 封装\n隐藏实现细节"));
        assertFalse(AgentOrchestratorService.shouldAutoFinishAfterWorker(
                "single", "[约束] 未提供 kbId"));
        assertFalse(AgentOrchestratorService.shouldAutoFinishAfterWorker(
                "single", "[Worker 错误] timeout"));
        assertFalse(AgentOrchestratorService.shouldAutoFinishAfterWorker("single", "  "));
    }

    /** 检索 query 用用户原问，不含会话历史与 Worker 任务拼装。 */
    @Test
    void kbSearchQueryUsesUserGoalNotHistory() {
        String query = AgentOrchestratorService.kbSearchQuery(
                "这个知识库总体而言讲了些什么？",
                "总结整体内容框架和主要章节");
        assertEquals("这个知识库总体而言讲了些什么？", query);
        assertFalse(query.contains("会话上下文"));
        assertFalse(query.contains("Java"));
    }

    @Test
    void kbSearchQueryFallsBackToInstruction() {
        assertEquals("总结文档", AgentOrchestratorService.kbSearchQuery("  ", "总结文档"));
    }

    @Test
    void kbWorkerPromptKeepsHistoryOutOfSearchAndAppendsContext() {
        String workerMsg = AgentOrchestratorService.buildWorkerUserMessage(
                "USER: Java 三大特性是什么\nASSISTANT: 封装继承多态",
                "总结整体内容");
        String prompt = AgentOrchestratorService.buildKbWorkerUserPrompt(workerMsg, "【文档目录】Java基础.md");
        assertTrue(prompt.contains("封装继承多态"));
        assertTrue(prompt.contains("【检索上下文】"));
        assertTrue(prompt.contains("文档目录"));
        assertEquals("这个知识库总体而言讲了些什么？",
                AgentOrchestratorService.kbSearchQuery("这个知识库总体而言讲了些什么？", "总结整体内容"));
    }

}
